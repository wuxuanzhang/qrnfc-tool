package com.qrnfc.tool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 轻量 LDAP 客户端（纯 Socket + BER 编解码，无任何第三方依赖）。
 * 支持 simple bind 与 subtree 搜索，用于 Android 查询 AD/LDAP。
 */
public class LdapClient {

    public static class LdapException extends Exception {
        public final String diagnostic;
        public LdapException(String message, String diagnostic) {
            super(message);
            this.diagnostic = diagnostic;
        }
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int msgId = 1;

    /** 建立连接（tls=true 时走 LDAPS 并信任自签名证书）。 */
    public void connect(String host, int port, boolean tls) throws Exception {
        if (tls) {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new TrustAllTrustManager()}, new SecureRandom());
            SSLSocketFactory f = sc.getSocketFactory();
            socket = f.createSocket(host, port);
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).startHandshake();
            }
        } else {
            socket = new Socket(host, port);
        }
        socket.setSoTimeout(20000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /** simple bind。dn 与 pwd 均空时匿名。 */
    public void bind(String dn, String pwd) throws Exception {
        byte[] version = tlv(0x02, new byte[]{3});
        byte[] name = tlv(0x04, dn.getBytes(StandardCharsets.UTF_8));
        byte[] auth = tlv(0x80, pwd.getBytes(StandardCharsets.UTF_8));
        byte[] body = concat(version, name, auth);
        byte[] bindReq = tlv(0x60, body); // [APPLICATION 0]
        send(bindReq);
        byte[] content = readMessage();
        int[] op = firstOp(content);
        // BindResponse: value[0]=ENUMERATED, value[1]=len, value[2]=resultCode
        int resultCode = (op[0] == 0x61 && content[op[1]] == 0x0A)
                ? (content[op[1] + 2] & 0xFF) : -1;
        if (resultCode != 0) {
            throw new LdapException("绑定失败",
                    "LDAP 错误码 " + resultCode + "：" + describeCode(resultCode));
        }
    }

    /** subtree 搜索，返回命中的条目列表（每条为 属性名->值列表）。 */
    public List<Map<String, List<String>>> search(String base, byte[] filter) throws Exception {
        List<Map<String, List<String>>> entries = new ArrayList<>();
        byte[] baseOct = tlv(0x04, base.getBytes(StandardCharsets.UTF_8));
        byte[] scope = tlv(0x0A, new byte[]{2});         // subtree
        byte[] deref = tlv(0x0A, new byte[]{0});
        byte[] size = tlv(0x02, new byte[]{0});
        byte[] time = tlv(0x02, new byte[]{0});
        byte[] typesOnly = tlv(0x01, new byte[]{0});
        byte[] attrs = tlv(0x30, tlv(0x04, "*".getBytes(StandardCharsets.UTF_8)));
        byte[] body = concat(baseOct, scope, deref, size, time, typesOnly, filter, attrs);
        byte[] searchReq = tlv(0x63, body); // [APPLICATION 3]
        send(searchReq);

        while (true) {
            byte[] content = readMessage();
            int[] op = firstOp(content);
            if (op[0] == 0x64) { // SearchResultEntry
                entries.add(parseEntry(content, op[1], op[2]));
            } else if (op[0] == 0x65) { // SearchResultDone
                int resultCode = content[op[1]] == 0x0A ? (content[op[1] + 2] & 0xFF) : -1;
                if (resultCode != 0) {
                    throw new LdapException("查询失败",
                            "LDAP 错误码 " + resultCode + "：" + describeCode(resultCode));
                }
                return entries;
            }
        }
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
    }

    // ---------- 过滤器构造 ----------
    public static byte[] eq(String attr, String value) {
        return tlv(0xA3, concat(tlv(0x04, attr.getBytes(StandardCharsets.UTF_8)),
                tlv(0x04, value.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * substring 过滤器（前缀/中缀/后缀模糊匹配）。
     * pattern 中 '*' 表示通配：首段为 initial [0]，中段为 any [1]，末段为 final [2]。
     * 例如 "PC01*" → (attr=PC01*)；"CX*$" → (attr=CX*$)。
     */
    public static byte[] substring(String attr, String pattern) {
        String[] parts = pattern.split("\\*", -1);
        List<byte[]> subElems = new ArrayList<>();
        if (!parts[0].isEmpty()) {
            subElems.add(tlv(0x80, parts[0].getBytes(StandardCharsets.UTF_8)));   // initial
        }
        for (int i = 1; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                subElems.add(tlv(0x81, parts[i].getBytes(StandardCharsets.UTF_8))); // any
            }
        }
        if (!parts[parts.length - 1].isEmpty()) {
            subElems.add(tlv(0x82, parts[parts.length - 1].getBytes(StandardCharsets.UTF_8))); // final
        }
        byte[] subseq = tlv(0x30, concatAll(subElems.toArray(new byte[0][])));
        return tlv(0xA4, concat(tlv(0x04, attr.getBytes(StandardCharsets.UTF_8)), subseq));
    }

    public static byte[] and(byte[]... fs) { return tlv(0xA0, concatAll(fs)); }
    public static byte[] or(byte[]... fs) { return tlv(0xA1, concatAll(fs)); }

    // ---------- 发送/读取 ----------
    private void send(byte[] op) throws Exception {
        byte[] msgIdBytes = intBytes(msgId++);
        byte[] message = tlv(0x30, concat(tlv(0x02, msgIdBytes), op));
        out.write(message);
        out.flush();
    }

    /** 读取一条 LDAPMessage（0x30 外层 SEQUENCE），返回其内容。 */
    private byte[] readMessage() throws Exception {
        int tag = readByte();
        if (tag != 0x30) throw new LdapException("协议错误", "非 SEQUENCE 消息: 0x" + Integer.toHexString(tag));
        int len = readLength();
        byte[] val = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(val, off, len - off);
            if (n < 0) throw new LdapException("连接中断", "读取到流末尾");
            off += n;
        }
        return val;
    }

    /** 定位消息内容里第一个 protocolOp 元素，返回 {opTag, valueStart, valueLen}。 */
    private int[] firstOp(byte[] content) {
        int i = 0;
        // 跳过 msgId (INTEGER)
        if (content[i] == 0x02) {
            int[] m = element(content, i);
            i = m[3];
        }
        return element(content, i);
    }

    /** 解析 SearchResultEntry：返回 属性名->值列表。 */
    private Map<String, List<String>> parseEntry(byte[] content, int start, int len) {
        Map<String, List<String>> map = new HashMap<>();
        // SearchResultEntry value = { OCTET STRING dn, SEQUENCE attributes }
        int[] dn = element(content, start);
        int attrsPos = dn[3]; // dn 元素结束 = attributes 起始
        int[] attrsElem = element(content, attrsPos);
        int q = attrsElem[1];
        int end = attrsElem[3];
        while (q < end) {
            int[] attrElem = element(content, q);
            int r = attrElem[1];
            int aEnd = attrElem[3];
            String type = null;
            List<String> values = new ArrayList<>();
            while (r < aEnd) {
                int[] e = element(content, r);
                if (e[0] == 0x04) { // OCTET STRING type
                    type = new String(content, e[1], e[2], StandardCharsets.UTF_8);
                } else if (e[0] == 0x31) { // SET of values
                    int s = e[1];
                    int sEnd = e[3];
                    while (s < sEnd) {
                        int[] v = element(content, s);
                        if (v[0] == 0x04) {
                            values.add(new String(content, v[1], v[2], StandardCharsets.UTF_8));
                        }
                        s = v[3];
                    }
                }
                r = e[3];
            }
            if (type != null) map.put(type, values);
            q = attrElem[3];
        }
        return map;
    }

    // ---------- BER 基础 ----------
    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        writeLength(out, value.length);
        out.write(value, 0, value.length);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int len) {
        if (len < 0x80) {
            out.write(len);
        } else {
            int n = 1;
            int v = len;
            while (v >= 0x100) { v >>= 8; n++; }
            out.write(0x80 | n);
            for (int i = 0; i < n; i++) {
                out.write((len >> (8 * (n - 1 - i))) & 0xFF);
            }
        }
    }

    private int readByte() throws Exception {
        int b = in.read();
        if (b < 0) throw new LdapException("连接中断", "读取到流末尾");
        return b;
    }

    private int readLength() throws Exception {
        int first = readByte();
        if ((first & 0x80) == 0) return first;
        int n = first & 0x7F;
        int len = 0;
        for (int i = 0; i < n; i++) len = (len << 8) | readByte();
        return len;
    }

    private static int[] element(byte[] data, int offset) {
        int tag = data[offset] & 0xFF;
        offset++;
        int first = data[offset] & 0xFF;
        offset++;
        int len;
        if ((first & 0x80) == 0) {
            len = first;
        } else {
            int n = first & 0x7F;
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (data[offset + i] & 0xFF);
            offset += n;
        }
        return new int[]{tag, offset, len, offset + len};
    }

    private static byte[] intBytes(int v) {
        if (v < 0x80) return new byte[]{(byte) v};
        if (v < 0x8000) return new byte[]{(byte) (v >> 8), (byte) v};
        return new byte[]{(byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] concat(byte[]... arr) {
        return concatAll(arr);
    }

    private static byte[] concatAll(byte[]... arr) {
        int total = 0;
        for (byte[] a : arr) total += a.length;
        byte[] out = new byte[total];
        int p = 0;
        for (byte[] a : arr) { System.arraycopy(a, 0, out, p, a.length); p += a.length; }
        return out;
    }

    private static String describeCode(int code) {
        switch (code) {
            case 0: return "成功";
            case 1: return "操作错误";
            case 2: return "协议错误";
            case 3: return "超出时间限制";
            case 4: return "超出大小限制";
            case 13: return "访问被拒绝(无权限)";
            case 16: return "无此对象(未找到)";
            case 32: return "无此对象/条目不存在";
            case 34: return "无效的 DN 语法";
            case 49: return "无效凭据(用户名或密码错误)";
            case 50: return "权限不足";
            case 51: return "服务器忙";
            case 52: return "服务器不可用";
            case 80: return "其他错误";
            default: return "未知(" + code + ")";
        }
    }

    /** 信任所有证书的 TrustManager（企业自签名证书场景）。 */
    private static class TrustAllTrustManager implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] c, String s) { }
        @Override public void checkServerTrusted(X509Certificate[] c, String s) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}

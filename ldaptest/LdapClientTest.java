package com.qrnfc.tool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 模拟 LDAP 服务器 + 客户端集成测试，验证 BER 编解码正确性（纯 JVM 运行）。
 */
public class LdapClientTest {

    public static void main(String[] args) throws Exception {
        new Thread(LdapClientTest::server).start();
        Thread.sleep(300);

        LdapClient c = new LdapClient();
        try {
            c.connect("127.0.0.1", 1389, false);
            c.bind("cn=admin,dc=example,dc=com", "secret");
            byte[] filter = LdapClient.and(
                    LdapClient.eq("objectClass", "computer"),
                    LdapClient.substring("cn", "*PC01*"));
            java.util.List<java.util.Map<String, java.util.List<String>>> list =
                    c.search("dc=example,dc=com", filter);
            if (list.size() != 1) {
                System.out.println("FAIL: expected 1 entry, got " + list.size());
                return;
            }
            java.util.Map<String, java.util.List<String>> e = list.get(0);
            System.out.println("parsed entry: " + e);
            if (!"PC01".equals(e.get("cn").get(0))
                    || !"computer".equals(e.get("objectClass").get(0))
                    || !"Win11".equals(e.get("operatingSystem").get(0))) {
                System.out.println("FAIL: wrong values");
                return;
            }
            System.out.println("TEST PASS");
        } finally {
            c.close();
        }
    }

    // ---------------- mock server ----------------
    static void server() {
        try (ServerSocket ss = new ServerSocket(1389)) {
            Socket s = ss.accept();
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            // 1) bind request
            byte[] bindContent = readMsg(in);
            int[] op = firstOp(bindContent);
            int msgId = readInteger(bindContent, 1);
            // respond bindResponse success
            byte[] bindResp = msg(0x61, concat(tlv(0x0A, new byte[]{0}),
                    tlv(0x04, new byte[0]), tlv(0x04, new byte[0])), msgId);
            out.write(bindResp); out.flush();

            // 2) search request
            byte[] searchContent = readMsg(in);
            int[] op2 = firstOp(searchContent);
            int msgId2 = readInteger(searchContent, 1);
            System.out.println("[server] got search, opTag=" + op2[0]
                    + " contentLen=" + searchContent.length);

            // 校验过滤器是否为 substring(0xA4) 且前缀正确
            byte[] body = java.util.Arrays.copyOfRange(searchContent, op2[1], op2[3]);
            int p = 0;
            for (int i = 0; i < 6; i++) { int[] e = element(body, p); p = e[3]; }
            java.util.List<String> subs = new java.util.ArrayList<>();
            walkFilters(body, p, subs);
            System.out.println("[server] substring filters: " + subs);
            boolean ok = subs.stream().anyMatch(x -> x.contains("cn=[81]PC01,"));
            if (!ok) System.out.println("SERVER_CHECK: FAIL (no cn=*PC01* substring)");
            else System.out.println("SERVER_CHECK: PASS (contains substring filter encoded correctly)");

            // respond SearchResultEntry
            byte[] dn = tlv(0x04, "cn=PC01,dc=example,dc=com".getBytes(StandardCharsets.UTF_8));
            byte[] attrs = tlv(0x30, concat(
                    attr("cn", "PC01"),
                    attr("objectClass", "computer"),
                    attr("operatingSystem", "Win11")));
            byte[] entryBody = concat(dn, attrs);
            System.out.println("[server] entryBody len=" + entryBody.length);
            byte[] entryMsg = msg(0x64, entryBody, msgId2);
            out.write(entryMsg);

            // respond SearchResultDone success (value = resultCode + matchedDN + diag)
            byte[] doneVal = concat(tlv(0x0A, new byte[]{0}),
                    tlv(0x04, new byte[0]), tlv(0x04, new byte[0]));
            out.write(msg(0x65, doneVal, msgId2));
            out.flush();
        } catch (Exception e) {
            System.out.println("server error: " + e);
        }
    }

    // 遍历过滤器树，收集 substring(0xA4) 元素
    static void walkFilters(byte[] d, int pos, java.util.List<String> out) {
        int[] e = element(d, pos);
        int tag = e[0];
        if (tag == 0xA4) {
            int[] te = element(d, e[1]);
            String type = new String(d, te[1], te[2], StandardCharsets.UTF_8);
            int[] subElem = element(d, te[3]);
            int q = subElem[1], end = subElem[3];
            StringBuilder sb = new StringBuilder(type).append("=");
            while (q < end) {
                int[] s = element(d, q);
                sb.append("[").append(Integer.toHexString(s[0])).append("]")
                        .append(new String(d, s[1], s[2], StandardCharsets.UTF_8)).append(",");
                q = s[3];
            }
            out.add(sb.toString());
        }
        if (tag == 0xA0 || tag == 0xA1) {
            int q = e[1], end = e[3];
            while (q < end) {
                walkFilters(d, q, out);
                q = element(d, q)[3];
            }
        }
    }

    static byte[] attr(String type, String val) {        return tlv(0x30, concat(tlv(0x04, type.getBytes(StandardCharsets.UTF_8)),
                tlv(0x31, tlv(0x04, val.getBytes(StandardCharsets.UTF_8)))));
    }

    static byte[] msg(int opTag, byte[] body, int msgId) {
        return tlv(0x30, concat(tlv(0x02, intBytes(msgId)), tlv(opTag, body)));
    }

    static byte[] readMsg(InputStream in) throws Exception {
        int tag = in.read();
        if (tag != 0x30) throw new RuntimeException("bad tag " + tag);
        int len = readLen(in);
        byte[] v = new byte[len];
        int off = 0;
        while (off < len) off += in.read(v, off, len - off);
        return v;
    }

    static int readLen(InputStream in) throws Exception {
        int first = in.read();
        if ((first & 0x80) == 0) return first;
        int n = first & 0x7F, len = 0;
        for (int i = 0; i < n; i++) len = (len << 8) | in.read();
        return len;
    }

    static int[] firstOp(byte[] content) {
        int i = 0;
        if (content[i] == 0x02) { int[] m = element(content, i); i = m[3]; }
        return element(content, i);
    }

    static int readInteger(byte[] content, int msgId) {
        int[] e = element(content, 1);
        // read msgId value
        return (content[e[1]] & 0xFF);
    }

    static int[] element(byte[] data, int offset) {
        int tag = data[offset] & 0xFF; offset++;
        int first = data[offset] & 0xFF; offset++;
        int len;
        if ((first & 0x80) == 0) len = first;
        else {
            int n = first & 0x7F; len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (data[offset + i] & 0xFF);
            offset += n;
        }
        return new int[]{tag, offset, len, offset + len};
    }

    static byte[] intBytes(int v) {
        if (v < 0x80) return new byte[]{(byte) v};
        return new byte[]{(byte) (v >> 8), (byte) v};
    }

    static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (value.length < 0x80) out.write(value.length);
        else {
            int n = 1, v = value.length;
            while (v >= 0x100) { v >>= 8; n++; }
            out.write(0x80 | n);
            for (int i = 0; i < n; i++) out.write((value.length >> (8 * (n - 1 - i))) & 0xFF);
        }
        out.write(value, 0, value.length);
        return out.toByteArray();
    }

    static byte[] concat(byte[]... arr) {
        int total = 0;
        for (byte[] a : arr) total += a.length;
        byte[] out = new byte[total];
        int p = 0;
        for (byte[] a : arr) { System.arraycopy(a, 0, out, p, a.length); p += a.length; }
        return out;
    }
}

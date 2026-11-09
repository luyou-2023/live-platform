package com.luyou.live.server.common;

public final class AuthContext {
    private static final ThreadLocal<String> UID = new ThreadLocal<>();
    public static void setUid(String uid) { UID.set(uid); }
    public static String uid() { return UID.get(); }
    public static String requireUid() {
        String uid = UID.get();
        if (uid == null) throw new ApiException(40100, "unauthorized");
        return uid;
    }
    public static void clear() { UID.remove(); }
}

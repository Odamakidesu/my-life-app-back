package com.mylifeapp.common.observability;

import org.slf4j.MDC;

/** MDC に載っている相関IDへのアクセス。ログとレスポンスで同じ値を使うための小さなヘルパ。 */
public final class TraceIds {

    public static final String TRACE_ID = "traceId";

    private TraceIds() {
    }

    public static String current() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null ? "unknown" : traceId;
    }
}

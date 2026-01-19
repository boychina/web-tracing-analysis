package com.krielwus.webtracinganalysis.info;

import com.krielwus.webtracinganalysis.entity.RefreshToken;

public class RefreshRotateResult {
    private final RefreshToken next;
    private final String refreshPlain;
    public RefreshRotateResult(RefreshToken next, String refreshPlain) {
        this.next = next;
        this.refreshPlain = refreshPlain;
    }
    public RefreshToken getNext() { return next; }
    public String getRefreshPlain() { return refreshPlain; }
}

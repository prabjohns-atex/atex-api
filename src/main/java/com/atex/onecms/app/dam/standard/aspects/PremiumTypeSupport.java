package com.atex.onecms.app.dam.standard.aspects;

public interface PremiumTypeSupport {
    String DEFAULT_PREMIUM_TYPE = "inherit";
    String getPremiumType();
    void setPremiumType(final String premiumType);
}


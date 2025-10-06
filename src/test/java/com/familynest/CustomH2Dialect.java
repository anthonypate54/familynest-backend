package com.familynest;

import org.hibernate.dialect.H2Dialect;

public class CustomH2Dialect extends H2Dialect {
    public CustomH2Dialect() {
        super();
    }

    @Override
    public String getCascadeConstraintsString() {
        return "";
    }
} 

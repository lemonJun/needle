package net.cassite.ioc.testioc;

import net.cassite.pure.aop.AOP;

@AOP(value = ReturnBeforeInvokingWeaver.class, useCglib = true)
public class ReturnBeforeInvokingAOPBean {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
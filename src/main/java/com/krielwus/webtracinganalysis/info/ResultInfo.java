package com.krielwus.webtracinganalysis.info;

public class ResultInfo implements java.io.Serializable{
    private int code;
    private String msg;
    private Object data;
    private String page;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public ResultInfo() {
    }

    public ResultInfo(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ResultInfo(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public ResultInfo(int code, String msg, Object data, String page) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.page = page;
    }

    @Override
    public String toString() {
        return "ResultInfo{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                ", page='" + page + '\'' +
                '}';
    }
}

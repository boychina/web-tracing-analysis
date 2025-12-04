package com.krielwus.webtracinganalysis.info;

/**
 * 通用接口返回体。
 * 统一封装后端接口的状态码、消息、数据载荷及页面跳转地址，
 * 便于前端解析与显示。
 */
public class ResultInfo implements java.io.Serializable{
    /** 业务状态码 */
    private int code;
    /** 业务提示消息 */
    private String msg;
    /** 业务数据载荷 */
    private Object data;
    /** 用于后端返回需要跳转的页面地址 */
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

    /** 默认构造方法 */
    public ResultInfo() {
    }

    /**
     * 仅包含状态码与消息的返回体。
     * @param code 状态码
     * @param msg 消息
     */
    public ResultInfo(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 包含状态码、消息与数据载荷的返回体。
     */
    public ResultInfo(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 包含状态码、消息、数据与跳转页面的返回体。
     */
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

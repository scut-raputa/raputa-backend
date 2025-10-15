package cn.scut.raputa.exception;

public class BizException extends RuntimeException {
    private final int status;
    private final int code;

    public BizException(int status, String message) {
        super(message);
        this.status = status;
        this.code = status;
    }

    public int getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
package cn.scut.raputa.exception;

import cn.scut.raputa.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
        @ExceptionHandler(BizException.class)
        public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
                return ResponseEntity
                                .status(ex.getStatus())
                                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleDiv(DataIntegrityViolationException ex) {
                return ResponseEntity
                                .status(409)
                                .body(ApiResponse.error(409, "该用户名已被注册"));
        }

        public ResponseEntity<ApiResponse<Void>> handleOptimistic(Exception ex) {
                return ResponseEntity
                                .status(409)
                                .body(ApiResponse.error(409, "数据已被他人修改，请刷新后重试"));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleBadJson(HttpMessageNotReadableException ex) {
                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(400, "请求体格式错误"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleMethodArgInvalid(MethodArgumentNotValidException ex) {
                String msg = ex.getBindingResult().getFieldErrors().stream()
                                .findFirst()
                                .map(fe -> formatFieldError(fe))
                                .orElse("请求参数不合法");
                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(400, msg));
        }

        @ExceptionHandler(BindException.class)
        public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
                String msg = ex.getBindingResult().getFieldErrors().stream()
                                .findFirst()
                                .map(this::formatFieldError)
                                .orElse("请求参数不合法");
                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(400, msg));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
                String msg = ex.getConstraintViolations().stream()
                                .findFirst()
                                .map(v -> v.getMessage())
                                .orElse("请求参数不合法");
                return ResponseEntity
                                .badRequest()
                                .body(ApiResponse.error(400, msg));
        }

        public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
                ex.printStackTrace();
                Throwable root = ex;
                while (root.getCause() != null)
                        root = root.getCause();
                String msg = root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage());
                return ResponseEntity
                                .status(500)
                                .body(ApiResponse.error(500, msg));
        }

        private String formatFieldError(FieldError fe) {
                return fe.getField() + " " + fe.getDefaultMessage();
        }
}

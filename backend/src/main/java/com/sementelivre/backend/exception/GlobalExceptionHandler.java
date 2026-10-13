package com.sementelivre.backend.exception;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, String> MENSAGENS_POR_CONSTRAINT = Map.of(
            "uk_pessoa_email", "E-mail já cadastrado no sistema.",
            "uk_pessoa_documento", "Documento já cadastrado no sistema.",
            "uk_proprietario_rg", "RG já cadastrado no sistema."
    );

    // Campo do DTO que cada constraint de unicidade protege; vai em fieldErrors para o front
    // mostrar o erro embaixo do campo certo.
    private static final Map<String, String> CAMPOS_POR_CONSTRAINT = Map.of(
            "uk_pessoa_email", "email",
            "uk_pessoa_documento", "documento",
            "uk_proprietario_rg", "rg"
    );

    private static final String MENSAGEM_GENERICA_INTEGRIDADE = "Violação de integridade nos dados.";

    //trata erro 404 (recurso não encontrado)
    @ExceptionHandler({ResourceNotFoundException.class, RecursoNaoEncontradoException.class, NoSuchElementException.class})
    public ResponseEntity<ErrorResponse> handleResourceNotFound(RuntimeException e, HttpServletRequest request){
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse err = new ErrorResponse(
            "Resource Not Found",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata os conflitos de negócio de pessoa (email/documento/RG duplicado), verificados no service antes de persistir.
    //o campo que colidiu vai em fieldErrors, no mesmo formato "campo: mensagem" dos erros de validação
    @ExceptionHandler({EmailJaCadastradoException.class, DocumentoJaCadastradoException.class, RgJaCadastradoException.class})
    public ResponseEntity<ErrorResponse> handleConflitosDePessoa(RuntimeException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        ErrorResponse err = new ErrorResponse(
            "Conflict",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            List.of(campoDoConflito(e) + ": " + e.getMessage())
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 409 - exclusão barrada por registros vinculados (pessoa com estoque/pedidos,
    //comunidade com propriedades etc.). Sem este handler a exceção chegava como 500.
    @ExceptionHandler(DependenciaVinculadaException.class)
    public ResponseEntity<ErrorResponse> handleDependenciaVinculada(DependenciaVinculadaException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        ErrorResponse err = new ErrorResponse(
            "Conflict",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 400 na troca de senha: a senha atual informada não confere
    @ExceptionHandler(SenhaAtualIncorretaException.class)
    public ResponseEntity<ErrorResponse> handleSenhaAtualIncorreta(SenhaAtualIncorretaException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ErrorResponse err = new ErrorResponse(
            "Bad Request",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            List.of("senhaAtual: " + e.getMessage())
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 429 - pedido do site público acima do limite (por IP ou por e-mail de destino)
    @ExceptionHandler(LimiteDePedidosExcedidoException.class)
    public ResponseEntity<ErrorResponse> handleLimiteDePedidos(LimiteDePedidosExcedidoException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;

        ErrorResponse err = new ErrorResponse(
            "Too Many Requests",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosArredondadosParaCima(e.getTentarDeNovoEm())))
                .body(err);
    }

    // Retry-After arredonda para cima: voltar antes da vaga abrir daria outro 429
    private static long segundosArredondadosParaCima(Duration espera) {
        return Math.max(1, espera.toSeconds() + (espera.toNanosPart() > 0 ? 1 : 0));
    }

    //trata erro 422 - pedido do site com tipo diferente da oferta, que o produtor não deixou negociar
    @ExceptionHandler(NegociacaoNaoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleNegociacaoNaoPermitida(NegociacaoNaoPermitidaException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        ErrorResponse err = new ErrorResponse(
            "Negotiation Not Allowed",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 422 - pedido com quantidade maior que o estoque disponível
    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handleEstoqueInsuficiente(EstoqueInsuficienteException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        ErrorResponse err = new ErrorResponse(
            "Insufficient Stock",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 400 (Bean Validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        //extrai os erros especificos de cada campo do DTO
        List<String> validationErrors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            validationErrors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        ErrorResponse err = new ErrorResponse(
            "Validation Error",
            "Um ou mais campos estão inválidos",
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            validationErrors
        );
        return ResponseEntity.status(status).body(err);
    }

    //trata erro 400 (documento com dígito verificador/tipo/tamanho inválido, validado no service)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ErrorResponse err = new ErrorResponse(
            "Bad Request",
            e.getMessage(),
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(status).body(err);
    }

    // Erros de upload chegavam como 500 sem mensagem legível no app.
    @ExceptionHandler(UploadFotoException.class)
    public ResponseEntity<ErrorResponse> handleUploadFoto(UploadFotoException e, HttpServletRequest request) {
        return uploadError(e.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    // Foto acima de spring.servlet.multipart.max-file-size.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return uploadError("A foto excede o tamanho máximo de 5 MB.", HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    private ResponseEntity<ErrorResponse> uploadError(String message, HttpStatus status, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.getReasonPhrase(),
                message,
                Instant.now(),
                status.value(),
                request.getRequestURI(),
                null
        ));
    }

    //trata erro 409 - conflito no bd (ex: corrida entre o check de unicidade do service e o insert;
    //a constraint UNIQUE do banco barra a segunda). Tenta identificar qual campo colidiu pelo nome da
    //constraint; se não reconhecer (FK, NOT NULL etc.), mantém a mensagem genérica.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseExceptions(DataIntegrityViolationException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        String constraint = constraintConhecida(e);
        String mensagem = constraint != null ? MENSAGENS_POR_CONSTRAINT.get(constraint) : MENSAGEM_GENERICA_INTEGRIDADE;
        List<String> fieldErrors = constraint != null
                ? List.of(CAMPOS_POR_CONSTRAINT.get(constraint) + ": " + mensagem)
                : null;

        ErrorResponse err = new ErrorResponse(
            "Database Conflict",
            mensagem,
            Instant.now(),
            status.value(),
            request.getRequestURI(),
            fieldErrors
        );
        return ResponseEntity.status(status).body(err);
    }

    private String campoDoConflito(RuntimeException e) {
        if (e instanceof EmailJaCadastradoException) {
            return "email";
        }
        if (e instanceof DocumentoJaCadastradoException) {
            return "documento";
        }
        return "rg";
    }

    //nome de comunidade parecido com um existente (CDU-21): a mensagem já traz a sugestão de nome;
    //vai também em fieldErrors para o front mostrar embaixo do campo "nome"
    @ExceptionHandler(NomeSimilarException.class)
    public ResponseEntity<ErrorResponse> handleNomeSimilar(NomeSimilarException e, HttpServletRequest request){
        HttpStatus status = HttpStatus.CONFLICT;

        ErrorResponse err = new ErrorResponse(
                "Similar Name",
                e.getMessage(),
                Instant.now(),
                status.value(),
                request.getRequestURI(),
                List.of("nome: " + e.getMessage())
        );
        return ResponseEntity.status(status).body(err);
    }

    @ExceptionHandler(TransicaoStatusInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleTransicaoStatusInvalida(TransicaoStatusInvalidaException e, HttpServletRequest request){
        HttpStatus status = HttpStatus.CONFLICT;

        ErrorResponse err = new ErrorResponse(
                "Invalid Status Transition",
                e.getMessage(),
                Instant.now(),
                status.value(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(err);
    }

    private String constraintConhecida(DataIntegrityViolationException ex) {
        String constraintName = extrairNomeConstraint(ex);
        if (constraintName != null && MENSAGENS_POR_CONSTRAINT.containsKey(constraintName)) {
            return constraintName;
        }

        // Fallback defensivo: getConstraintName() pode vir nulo dependendo do driver/dialect;
        // nesse caso tenta reconhecer a constraint pelo texto da mensagem da causa raiz.
        Throwable causaMaisEspecifica = ex.getMostSpecificCause();
        String mensagemCausa = causaMaisEspecifica != null ? causaMaisEspecifica.getMessage() : null;
        if (mensagemCausa != null) {
            for (String constraint : MENSAGENS_POR_CONSTRAINT.keySet()) {
                if (mensagemCausa.contains(constraint)) {
                    return constraint;
                }
            }
        }

        return null;
    }

    private String extrairNomeConstraint(Throwable ex) {
        Throwable atual = ex;
        while (atual != null) {
            if (atual instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            atual = atual.getCause();
        }
        return null;
    }
}

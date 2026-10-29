package odontomatch;

/** Erro de validação exibido ao usuário. */
public class Bad extends RuntimeException {
    public Bad(String msg) { super(msg); }
}

package cl.campuslab.bookings.messaging;

/**
 * Nombres de intercambios, colas y claves de enrutamiento.
 */
public final class RabbitNames {

    private RabbitNames() {
    }

    // Intercambios
    public static final String COMMAND_EXCHANGE = "cmd.direct";
    public static final String TOPIC_EXCHANGE = "cmd.topic";
    public static final String DEAD_LETTER_EXCHANGE = "cmd.dead.dlx";

    // Comandos de stock
    public static final String STOCK_QUEUE = "q.cmd.stock";
    public static final String STOCK_ROUTING_KEY = "stock.command";
    public static final String STOCK_DLQ = "q.cmd.stock.dlq";

    // Resultados de las operaciones de stock
    public static final String STOCK_RESULT_QUEUE = "q.result.stock";
    public static final String STOCK_RESULT_ROUTING_KEY = "stock.result";
    public static final String STOCK_RESULT_DLQ = "q.result.stock.dlq";

    // Notificaciones por correo
    public static final String EMAIL_QUEUE = "q.cmd.email";
    public static final String EMAIL_ROUTING_KEY = "email.send";
    public static final String EMAIL_TOPIC_PATTERN = "email.*";
    public static final String EMAIL_DLQ = "q.cmd.email.dlq";

    // Tickets de preparacion
    public static final String PREP_QUEUE = "q.cmd.prep";
    public static final String PREP_ROUTING_KEY = "prep.ticket";
    public static final String PREP_TOPIC_PATTERN = "prep.#";
    public static final String PREP_DLQ = "q.cmd.prep.dlq";

    // Comprobantes de reserva
    public static final String VOUCHER_QUEUE = "q.cmd.voucher";
    public static final String VOUCHER_ROUTING_KEY = "voucher.gen";
    public static final String VOUCHER_TOPIC_PATTERN = "voucher.*";
    public static final String VOUCHER_DLQ = "q.cmd.voucher.dlq";
}
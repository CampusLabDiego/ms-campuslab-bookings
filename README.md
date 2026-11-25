# Microservicio de reservas CampusLab

Gestiona las reservas de recursos académicos y coordina los cambios de estado y movimientos de stock mediante mensajería.

## Tecnologías

- Java 21.
- Spring Boot.
- Spring Web MVC y Bean Validation.
- Spring Security OAuth2 Resource Server.
- Spring Data JPA y MySQL.
- Spring AMQP y RabbitMQ.
- Spring Kafka.
- Maven y Docker.

## Funcionalidades

- Crear y consultar reservas.
- Consultar las reservas propias del Cliente.
- Validar transiciones de estado.
- Confirmar la llegada del propietario de una reserva cuando corresponda.
- Solicitar movimientos de stock al catálogo.
- Generar tareas de correo, preparación y comprobantes.
- Publicar eventos de auditoría en RabbitMQ y Kafka.

## Estados de reserva

```text
SOLICITADA
APROBADA
EN_PREPARACION
EN_USO
DEVUELTA
CANCELADA
```

Las transiciones permitidas se validan en el backend.

## API principal

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/bookings` | Consultar reservas autorizadas |
| GET | `/api/bookings/{id}` | Consultar una reserva |
| POST | `/api/bookings` | Crear una reserva |
| PATCH | `/api/bookings/{id}/status?status=ESTADO` | Solicitar cambio de estado |
| POST | `/api/bookings/{id}/arrival` | Confirmar llegada |

Las rutas de negocio requieren un Access Token válido. La autorización también se aplica según el rol y la propiedad de la reserva.

## Procesamiento asíncrono

Los movimientos de stock se procesan mediante RabbitMQ:

1. Bookings valida la solicitud y guarda el cambio y el mensaje pendiente en una outbox de MySQL.
2. El productor publica el comando en RabbitMQ.
3. Catalog procesa el stock y publica el resultado.
4. Bookings recibe el resultado y confirma o rechaza el cambio pendiente.
5. Se generan las tareas y los eventos relacionados con la operación.

La interfaz puede mostrar temporalmente que un cambio está pendiente mientras se procesa.

## Outbox e idempotencia

Las publicaciones pendientes se guardan en MySQL dentro de la transacción de negocio. El productor utiliza confirmaciones del broker y conserva los eventos pendientes para reintentar una publicación no confirmada.

Los resultados de stock y los eventos mantienen identificadores para reconocer entregas repetidas y evitar duplicar operaciones.

## Publicación de eventos en Kafka

Los eventos de auditoría se publican en:

- `bookings.events`: eventos del ciclo de vida de las reservas.
- `audit.timeline`: eventos destinados al historial de auditoría.

El productor publica los eventos en RabbitMQ y Kafka. Kafka utiliza confirmaciones (`acks=all`) e idempotencia. Cuando una publicación no se confirma, el evento queda pendiente para reintento.

La conexión se configura mediante:

```text
KAFKA_BOOTSTRAP_SERVERS
```

En Docker Compose se debe configurar con las direcciones privadas de los brokers Kafka.

## Auditoría

Se generan eventos para la creación de una reserva, las solicitudes de cambio de estado, las confirmaciones o rechazos y la confirmación de llegada.

El actor se obtiene del token validado. Las respuestas automáticas se identifican como acciones del sistema y se relacionan con la solicitud mediante el identificador de operación.

## RabbitMQ

Bookings utiliza los exchanges `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` para coordinar comandos de stock, resultados, tareas y eventos.

Los nombres de exchanges, colas y routing keys están centralizados en las clases de mensajería del servicio.

## Configuración

Variables principales:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
CATALOG_URL
RABBITMQ_ADDRESSES
RABBITMQ_USER
RABBITMQ_PASSWORD
RABBITMQ_VHOST
KAFKA_BOOTSTRAP_SERVERS
```

Base de datos: `campuslab_bookings`  
Puerto interno: `8082`

Las credenciales y direcciones del entorno se configuran en el `.env` de infraestructura. No se deben subir secretos al repositorio.

## Compilación

En PowerShell, desde la carpeta del repositorio:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Este comando compila y empaqueta el servicio, pero omite la ejecución de pruebas.

## Despliegue

El servicio se construye y ejecuta mediante Docker Compose en el repositorio `infra-campuslab`.

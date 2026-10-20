# Microservicio de reservas CampusLab

Gestiona reservas de recursos académicos y coordina sus cambios de
estado con catálogo mediante RabbitMQ.

## Tecnologías

- Java 21 y Spring Boot.
- Spring Web MVC.
- Spring Security OAuth2 Resource Server.
- Spring Data JPA y MySQL.
- Spring AMQP.
- Maven y Docker.

## Funcionalidades

- Crear y consultar reservas.
- Filtrar las reservas del Cliente por el identificador oid.
- Gestionar transiciones de estado.
- Confirmar llegada del Cliente propietario bajo las reglas de horario.
- Solicitar movimientos de stock.
- Generar tareas de correo, preparación y comprobantes.
- Publicar eventos de auditoría.

## Estados

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

| Método | Ruta | Finalidad |
|---|---|---|
| GET | /api/bookings | Consultar reservas autorizadas |
| GET | /api/bookings/{id} | Consultar una reserva |
| POST | /api/bookings | Crear reserva |
| PATCH | /api/bookings/{id}/status?status=ESTADO | Solicitar cambio |
| POST | /api/bookings/{id}/arrival | Confirmar llegada |

## Procesamiento asíncrono

Al solicitar un cambio:

1. Se valida la transición.
2. Se guarda pendingStatus y el comando en la outbox.
3. Se responde sin esperar el procesamiento en catálogo.
4. El productor publica el comando.
5. Catálogo procesa el stock y publica su resultado.
6. Bookings confirma o rechaza el cambio pendiente.
7. Se generan las tareas y los eventos correspondientes.

La interfaz puede mostrar temporalmente un cambio pendiente.

## Outbox e idempotencia

Las publicaciones pendientes se guardan en MySQL dentro de la
transacción de negocio.

Los productores utilizan confirmaciones del broker y reintentan
publicaciones no confirmadas.

El resultado de stock se procesa de forma idempotente.
Las tareas y los eventos de auditoría conservan sus identificadores
durante los reintentos.

## Exchanges utilizados

- cmd.direct: comandos y resultados de stock, tareas de preparación.
- cmd.topic: correos, comprobantes y auditoría.
- cmd.dead.dlx: enrutamiento de mensajes rechazados.

La configuración está centralizada en RabbitNames y RabbitTopologyConfig.

## Auditoría

Se publican eventos de:

- Creación de reserva.
- Solicitud de cambio de estado.
- Confirmación o rechazo del cambio.
- Confirmación de llegada.

El actor de una solicitud se obtiene del JWT validado.
Los resultados automáticos registran SYSTEM y se relacionan con la
solicitud mediante operationId.

## Configuración

Variables principales:

- DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD.
- CATALOG_URL.
- RABBITMQ_ADDRESSES, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_VHOST.

Base de datos: campuslab_bookings.
Puerto del servicio: 8082.

## Compilación

```powershell
.\mvnw.cmd clean package -DskipTests
```

Este comando compila y empaqueta; omite la ejecución de pruebas.

El despliegue está definido en infra-campuslab.
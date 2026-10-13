# 2. La base local como única fuente de verdad

## Contexto

La app se usa en la carretera, a menudo con poca cobertura. El Ministerio publica los precios varias veces al día y
su API a veces tarda o falla. El histórico solo se puede pedir día a día y por provincia: unos 500 KB por día y
provincia.

## Decisión

- La interfaz **solo lee de SQLite** (SQLDelight), y lo observa con `Flow`. La red solo escribe en la base.
- Los precios actuales se refrescan si lo guardado tiene más de 30 minutos. Al refrescar a mano se fuerza.
- El histórico se guarda por días. Al abrir una ficha se piden solo los días que faltan, como mucho cuatro a la vez,
  y se guardan para toda la provincia. Un día que falla se omite y se vuelve a intentar la vez siguiente; nunca se
  rellena con un precio inventado.
- Lo guardado dura dos meses: la app enseña 30 días.
- Cada error de red se convierte en `ErrorDeRed`, con un mensaje para la persona. Lo guardado no se toca y la
  pantalla dice de cuándo son los datos.

## Alternativas descartadas

- **Pedir siempre a la red y guardar una caché aparte**: dos caminos para los mismos datos, y el modo sin conexión
  se convierte en un caso especial con sus propios errores.
- **Un servidor propio** que guarde el histórico de toda España: daría el histórico al instante, pero obliga a
  mantener un servicio. Para una app de consulta personal, el histórico propio basta.

## Consecuencias

- Abrir la app sin conexión enseña lo último que se vio.
- La primera ficha de una provincia tarda unos segundos (descarga hasta 29 días); las siguientes son instantáneas.
- Las pruebas del repositorio usan SQLite real en memoria y un servidor simulado que cuenta las peticiones. Así se
  comprueba que no se pide nada dos veces.

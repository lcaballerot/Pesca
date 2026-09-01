# Pesca

Plugin de **torneos de pesca** para MineToy (Paper/Purpur 1.21). Convierte las capturas
hechas dentro de una zona marcada en peces personalizados con su propia **rareza, peso,
puntos y precio**. Fuera de esa zona, el mapa pesca exactamente como el Minecraft de siempre.

- **Versión:** 1.0.0
- **API:** 1.21
- **Comando:** `/pesca` (alias `/pescar`, `/fishing`)
- **Almacenamiento:** SQLite (`sqlite-jdbc`)
- **Dependencia opcional:** Vault (economía)

## Características

- **Peces con identidad** — cada especie tiene rareza, rango de peso, puntos y precio. La
  especie y el peso se guardan en los datos persistentes del objeto, así que renombrar un
  pez en un yunque no cambia lo que vale.
- **Torneos y ranking** — puntúa mientras el torneo está activo, con clasificación en vivo
  y anuncios de capturas notables.
- **Cebo (cubo de cebo)** — llevarlo da a cada captura una probabilidad (`1 / chance-one-in`)
  de subir de rareza y ganar peso extra. Solo se gasta cuando acierta.
- **Tienda y venta** — compra cañas y cebo; vende peces por su identidad real.
- **Peso sesgado** — la tirada de peso tiende a lo pequeño (`weight-curve`), para que un
  trofeo signifique algo.

## Comandos y permisos

| Comando | Qué hace | Permiso |
|---|---|---|
| `/pesca top` | Clasificación del torneo | `pesca.use` (por defecto: todos) |
| `/pesca shop` | Abre la tienda (alias `tienda`) | `pesca.use` |
| `/pesca sell hand` | Vende el pez en la mano (`vender mano`) | `pesca.use` |
| `/pesca sell all` | Vende todos tus peces (`todo`) | `pesca.use` |
| `/pesca admin …` | `setarea`, `loot`, `duration`, `frequency`, `forcestart`, `forcestop`, `broadcasts`, `info`, `reload` | `pesca.admin` (op) |

## Compilar

```bash
mvn clean package
```

El `.jar` queda en `target/Pesca-1.0.0.jar`.

## Configuración

- `src/main/resources/` — los ficheros **por defecto** que trae el plugin (`config.yml`,
  `messages.yml`, `plugin.yml`).
- `config-actual/` — los ficheros de configuración **en uso ahora mismo** en el servidor de
  MineToy (`config.yml`, `messages.yml`, `area.yml`), para referencia.

## Sobre la velocidad de pesca

Pesca **no modifica la velocidad de la pesca vanilla**. El listener solo reacciona en el
instante `CAUGHT_FISH` (después de que el pez ya picó) y únicamente **cambia el objeto
capturado**; no llama a ningún método de temporización del anzuelo
(`setWaitTime` / `setMinWaitTime` / `setMaxWaitTime` / `setApplyLure` / `setBiteChance`).
Lo único que puede hacer que alguien pesque más rápido es un objeto normal del juego: la
**caña experta** de la tienda lleva el encantamiento vanilla **Atracción (Lure) III**,
idéntico a encantarla en un yunque.

Documentación detallada (ES): ver la guía técnica del plugin.

---

MineToy · Plugin Pesca

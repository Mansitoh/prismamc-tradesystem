# Video: https://youtu.be/Lz2Fg1_H_yQ


# Phase 2 – Trade Plugin Submission

## Introduction

First of all, thank you very much for accepting me into **Phase 1** of the selection process. I truly appreciate the opportunity to be considered as a part of the **PrismaMC** team.

## About the Plugin

For this second phase, we were asked to develop a plugin. Although I joined a bit later than the rest of the candidates, I still gave my best effort to complete it.

This plugin was developed using **Purpur**, which supports the **MiniMessage** library natively. If you intend to run it on **Spigot**, please make sure to include the **MiniMessage** dependency to ensure full compatibility.

## Development Process

Before starting the actual implementation, I usually outline the core structure using **Mermaid** for better logic visualization. Initially, I attempted to create a complete Mermaid flow, which took a significant amount of time (almost an entire day, on **Monday, July 14th**). To save time, I later used a simplified base structure so I could focus on coding.

Here’s the logic flow of the plugin, represented in Mermaid:

```mermaid
---
config:
  theme: redux
  layout: fixed
---
flowchart TD
    A(["/trade &lt;jugador2&gt;<br>"]) --> B{"Puso como argumento el jugador2?"}
    B --> C["Si"] & D["No"]
    D --> n1["Enviar mensaje de help"]
    C --> n2["Alguna vez el jugador2 se conectó?"]
    n2 --> n4["No"] & n6["Si"]
    n4 --> n5["Enviar mensaje de Jugador2 no encontrado"]
    n3(["Player Join Listener"]) --> n11["Agregar a jugador a lista de personas y enviar mensaje si es que tiene un trade activo"]
    n12(["Inventario Trade Abierto"]) --> n13["Cerró el inventario con items dentro con la tecla esc?"]
    n13 --> n14["Si"] & n16["No"]
    n14 --> n15["Devolver Items"]
    n16 --> n17["Le dió click a guardar items?"]
    n17 --> n18["No"] & n19["Si"]
    n18 --> n15
    n20["Está conectado a Redis?"] --> n21["Si"] & n22["No"]
    n19 --> n24["Jugador está conectado en el mismo server?"]
    n24 --> n25["Si"] & n26["No"]
    n25 --> n27["Enviar mensaje a jugador2 de nuevo trade y enviar mensaje a jugador1 de trade enviado<br>"]
    n26 --> n20
    n6 --> n10["Abrir inventario para tradeo al jugador 1"]
    n21 --> n28["Enviar solicitud de trade a travez de redis para que el jugador2 reciba el mensaje"]
    n22 --> n29@{ label: "<span style=\"color:\">Agregar Trade a la database MongoDB</span>" }
    n28 --> n29
    n27 --> n29
    n30(["Jugador2 mira el trade"]) --> n31["Jugador2 aceptó el trade?"]
    n31 --> n32["Si"] & n33["No"]
    n2@{ shape: diam}
    n13@{ shape: diam}
    n17@{ shape: diam}
    n18@{ shape: rect}
    n19@{ shape: rect}
    n20@{ shape: diam}
    n24@{ shape: diam}
    n25@{ shape: rect}
    n26@{ shape: rect}
    n29@{ shape: rect}
    n31@{ shape: diam}
     n1:::Aqua
     n1:::Rose
     n5:::Aqua
     n5:::Rose
     n15:::Rose
     n27:::Aqua
     n10:::Aqua
     n10:::Sky
     n29:::Aqua
    classDef Sky stroke-width:1px, stroke-dasharray:none, stroke:#374D7C, fill:#E2EBFF, color:#374D7C
    classDef Rose stroke-width:1px, stroke-dasharray:none, stroke:#FF5978, fill:#FFDFE5, color:#8E2236
    classDef Aqua stroke-width:1px, stroke-dasharray:none, stroke:#46EDC8, fill:#DEFFF8, color:#378E7A
```

## Technical Highlights
Designed to be fully asynchronous, ensuring performance and scalability.

Plugin is capable of handling multiple simultaneous trades.

Includes logic for cross-server trading, with a plan to use Redis for inter-server communication (the Redis event system was planned but not fully implemented due to time constraints).

Database interactions are optimized to avoid blocking the main thread.

## Limitations
Due to the short timeframe, I didn’t have the chance to perform extensive testing. Additionally, some messages and GUI-related polish remain incomplete. However, the core functionality and structure are in place and demonstrate a scalable, clean, and professional foundation.

## Final Notes
While this submission might not be 100% polished, I aimed to prioritize clean code, maintainability, and correct architectural decisions. I believe this reflects my potential as a developer.

Although GUI creation is not my strongest suit, I truly enjoy working at the packet level. I'm always open to learning new things, and if you give me the opportunity to be part of PrismaMC, I promise I will never let you down — whether for this or any future plugin you may need.

Thank you very much for reading.
Sincerely,
Mansitoh




--------------------------------------------
# Fase 2 – Envío del Plugin de Intercambio

## Introducción

Antes que nada, muchas gracias por haberme aceptado en la **Fase 1** del proceso de selección. Aprecio sinceramente la oportunidad de ser considerado para formar parte del equipo de **PrismaMC**.

## Sobre el Plugin

Para esta segunda fase se nos solicitó desarrollar un plugin. Aunque ingresé un poco más tarde que el resto de los participantes, me esforcé al máximo por cumplir con el objetivo a tiempo.

Este plugin fue desarrollado utilizando **Purpur**, el cual cuenta con soporte nativo para la librería **MiniMessage**. Si desean ejecutarlo en un servidor **Spigot**, es necesario incluir la dependencia de **MiniMessage** para asegurar su correcto funcionamiento.

## Proceso de Desarrollo

Antes de comenzar la implementación, normalmente estructuro la lógica principal del plugin utilizando **Mermaid**, lo cual me ayuda a visualizar el flujo completo. Al inicio, intenté crear una estructura detallada desde cero, lo cual me llevó casi un día entero (el **lunes 14 de julio**). Para optimizar el tiempo restante, utilicé luego una base más simplificada y así poder avanzar con el desarrollo.

A continuación, presento el flujo de lógica del plugin representado en Mermaid:

```mermaid
---
config:
  theme: redux
  layout: fixed
---
flowchart TD
    A(["/trade &lt;jugador2&gt;<br>"]) --> B{"Puso como argumento el jugador2?"}
    B --> C["Si"] & D["No"]
    D --> n1["Enviar mensaje de help"]
    C --> n2["Alguna vez el jugador2 se conectó?"]
    n2 --> n4["No"] & n6["Si"]
    n4 --> n5["Enviar mensaje de Jugador2 no encontrado"]
    n3(["Player Join Listener"]) --> n11["Agregar a jugador a lista de personas y enviar mensaje si es que tiene un trade activo"]
    n12(["Inventario Trade Abierto"]) --> n13["Cerró el inventario con items dentro con la tecla esc?"]
    n13 --> n14["Si"] & n16["No"]
    n14 --> n15["Devolver Items"]
    n16 --> n17["Le dió click a guardar items?"]
    n17 --> n18["No"] & n19["Si"]
    n18 --> n15
    n20["Está conectado a Redis?"] --> n21["Si"] & n22["No"]
    n19 --> n24["Jugador está conectado en el mismo server?"]
    n24 --> n25["Si"] & n26["No"]
    n25 --> n27["Enviar mensaje a jugador2 de nuevo trade y enviar mensaje a jugador1 de trade enviado<br>"]
    n26 --> n20
    n6 --> n10["Abrir inventario para tradeo al jugador 1"]
    n21 --> n28["Enviar solicitud de trade a travez de redis para que el jugador2 reciba el mensaje"]
    n22 --> n29@{ label: "<span style=\"color:\">Agregar Trade a la database MongoDB</span>" }
    n28 --> n29
    n27 --> n29
    n30(["Jugador2 mira el trade"]) --> n31["Jugador2 aceptó el trade?"]
    n31 --> n32["Si"] & n33["No"]
    n2@{ shape: diam}
    n13@{ shape: diam}
    n17@{ shape: diam}
    n18@{ shape: rect}
    n19@{ shape: rect}
    n20@{ shape: diam}
    n24@{ shape: diam}
    n25@{ shape: rect}
    n26@{ shape: rect}
    n29@{ shape: rect}
    n31@{ shape: diam}
     n1:::Aqua
     n1:::Rose
     n5:::Aqua
     n5:::Rose
     n15:::Rose
     n27:::Aqua
     n10:::Aqua
     n10:::Sky
     n29:::Aqua
    classDef Sky stroke-width:1px, stroke-dasharray:none, stroke:#374D7C, fill:#E2EBFF, color:#374D7C
    classDef Rose stroke-width:1px, stroke-dasharray:none, stroke:#FF5978, fill:#FFDFE5, color:#8E2236
    classDef Aqua stroke-width:1px, stroke-dasharray:none, stroke:#46EDC8, fill:#DEFFF8, color:#378E7A
```

## Aspectos Técnicos Destacados
Diseñado para ser completamente asíncrono, asegurando rendimiento y escalabilidad.

Capaz de manejar múltiples intercambios simultáneos entre jugadores.

Incluye la lógica necesaria para permitir intercambios entre servidores, con planes de integración con Redis (esta parte quedó parcialmente pendiente por cuestiones de tiempo).

Las interacciones con la base de datos están optimizadas para evitar bloqueos del hilo principal.

## Limitaciones
Por cuestiones de tiempo, no tuve la posibilidad de realizar pruebas exhaustivas del plugin. Además, algunos mensajes y aspectos visuales (GUI) quedaron sin terminar. Aun así, la funcionalidad principal y la estructura base están correctamente implementadas y reflejan una base escalable, limpia y profesional.

## Notas Finales
Aunque esta entrega no está completamente pulida, prioricé la calidad del código, su mantenibilidad y una arquitectura adecuada. Considero que esto representa fielmente mi perfil como desarrollador.

Si bien mi fortaleza no está en el desarrollo de interfaces gráficas (GUI), realmente disfruto trabajar a nivel de paquetes. Siempre estoy abierto a seguir aprendiendo, y si se me brinda la oportunidad de formar parte de PrismaMC, les aseguro que no los defraudaré, ya sea para este plugin o para cualquier otro proyecto futuro.

Muchas gracias por su tiempo y lectura.
Atentamente,
Mansitoh
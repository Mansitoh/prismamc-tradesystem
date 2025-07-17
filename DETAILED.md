# PrismaMC Trade System Plugin

## 📋 Available Commands / Comandos Disponibles

### 🔄 **Player Commands / Comandos de Jugador**

#### `/trade <player>`
**Description:** Initiate a trade request with another player  
**Descripción:** Iniciar una solicitud de trade con otro jugador  
**Permission:** None required / No requiere permisos  
**Usage Examples:**
- `/trade Steve` - Send a trade request to Steve
- `/trade María` - Enviar solicitud de trade a María

**Aliases:** None / Ninguno

---

#### `/mytrades`
**Description:** Open your personal trades management interface  
**Descripción:** Abrir tu interfaz personal de gestión de trades  
**Permission:** None required / No requiere permisos  
**Features:**
- View all your active, pending, completed, and cancelled trades
- Collect items from completed trades
- Manage your trade history with filters
- Add items to pending trades

**Funcionalidades:**
- Ver todos tus trades activos, pendientes, completados y cancelados
- Recoger items de trades completados
- Gestionar tu historial de trades con filtros
- Agregar items a trades pendientes

---

#### `/tradeconfirm <player> <tradeId>`
**Description:** Confirm and complete a trade with another player  
**Descripción:** Confirmar y completar un trade con otro jugador  
**Permission:** None required / No requiere permisos  
**Usage Examples:**
- `/tradeconfirm Steve 12345` - Confirm trade #12345 with Steve
- `/tradeconfirm María 67890` - Confirmar trade #67890 con María

**Note:** Both players must confirm for the trade to complete  
**Nota:** Ambos jugadores deben confirmar para que el trade se complete

---

#### `/traderesponse <accept|decline> <tradeId>`
**Description:** Accept or decline a pending trade request  
**Descripción:** Aceptar o rechazar una solicitud de trade pendiente  
**Permission:** None required / No requiere permisos  
**Usage Examples:**
- `/traderesponse accept 12345` - Accept trade request #12345
- `/traderesponse decline 12345` - Decline trade request #12345
- `/traderesponse aceptar 67890` - Aceptar solicitud de trade #67890
- `/traderesponse rechazar 67890` - Rechazar solicitud de trade #67890

**Aliases:** `/tr`

---

### 🛡️ **Admin Commands / Comandos de Administrador**

#### `/viewtrades <player>`
**Description:** View all trades of any player (Admin only)  
**Descripción:** Ver todos los trades de cualquier jugador (Solo Admin)  
**Permission:** `prismamc.trade.admin.viewtrades`  
**Usage Examples:**
- `/viewtrades Steve` - View all trades involving Steve
- `/viewtrades María` - Ver todos los trades que involucran a María

**Aliases:** `/vt`, `/admintrades`

**Features:**
- View trades of offline players
- Read-only access to all trade data
- Filter trades by status (All, Pending, Active, Completed, Cancelled)
- Dual-view system (Left click: Player 1 items, Right click: Player 2 items)
- Language selector for admin interface
- Full audit logging of admin actions

**Funcionalidades:**
- Ver trades de jugadores desconectados
- Acceso de solo lectura a todos los datos de trade
- Filtrar trades por estado (Todos, Pendientes, Activos, Completados, Cancelados)
- Sistema de vista dual (Click izquierdo: items Jugador 1, Click derecho: items Jugador 2)
- Selector de idioma para interfaz de admin
- Registro completo de auditoría de acciones de admin

---

## 🎮 **Usage Flow / Flujo de Uso**

### **Starting a Trade / Iniciar un Trade**
1. Use `/trade <player>` to send a trade request
2. Add items to your trade offer in the GUI
3. Confirm your items to send the request
4. Wait for the other player to respond

### **Responding to a Trade / Responder a un Trade**
1. Receive notification when someone sends you a trade
2. Use `/mytrades` to see pending requests
3. Click on the trade to add your items
4. Use `/tradeconfirm <player> <tradeId>` to finalize

### **Managing Your Trades / Gestionar tus Trades**
1. Use `/mytrades` to access your trade interface
2. Filter trades by status using the GUI buttons
3. Collect completed trade items by clicking on them
4. View detailed information about each trade

### **Admin Monitoring / Monitoreo de Admin**
1. Use `/viewtrades <player>` to inspect any player's trades
2. Use the language selector (slot 52) to change interface language
3. Left/Right click on trades to view specific player items
4. All admin actions are logged for audit purposes

---

## 🔑 **Permissions / Permisos**

| Permission | Description | Default |
|------------|-------------|---------|
| `prismamc.trade.admin.viewtrades` | Access to admin trade viewing commands | OP only |
| `prismamc.trade.use` | Basic trade functionality | All players |

---

## 🌍 **Multi-Language Support / Soporte Multi-Idioma**

The plugin supports multiple languages with automatic detection:
- 🇺🇸 **English** (en) - Default language
- 🇪🇸 **Español** (es) - Full Spanish translation

**Language Switching:**
- Players: Language is detected automatically or can be set via admin
- Admins: Use the language selector in admin GUIs (🌍 icon in slot 52)

**Cambio de Idioma:**
- Jugadores: El idioma se detecta automáticamente o puede ser establecido por admin
- Administradores: Usar el selector de idioma en las GUIs de admin (ícono 🌍 en slot 52)

---

## 🖱️ **Command Design Philosophy / Filosofía de Diseño de Comandos**

### **Why Commands Seem "Unusual" / Por Qué los Comandos Parecen "Extraños"**

The command structure of this plugin might appear unconventional at first glance, but it's **intentionally designed** around a **clickable text interaction system** that prioritizes **user experience** and **rapid interaction**.

Los comandos de este plugin pueden parecer poco convencionales a primera vista, pero están **diseñados intencionalmente** alrededor de un **sistema de interacción de texto clickeable** que prioriza la **experiencia del usuario** y la **interacción rápida**.

### **🎯 Core Design Principles / Principios de Diseño Centrales**

#### **1. Click-to-Execute Philosophy / Filosofía de Click-para-Ejecutar**
Instead of forcing players to memorize and type complex commands, the plugin generates **dynamic clickable messages** that allow users to interact with trades through simple clicks.

En lugar de obligar a los jugadores a memorizar y escribir comandos complejos, el plugin genera **mensajes clickeables dinámicos** que permiten a los usuarios interactuar con trades mediante simples clicks.

**Example Flow / Ejemplo de Flujo:**
```
Player receives trade notification:
📬 Steve sent you a trade! (ID: 12345)
[📦 CLICK TO VIEW THEIR ITEMS] ← Executes /traderesponse accept 12345
[❌ DECLINE TRADE] ← Executes /traderesponse decline 12345
```

#### **2. Context-Aware Command Generation / Generación de Comandos Consciente del Contexto**
The system **automatically generates** the appropriate commands with **pre-filled parameters** based on the current context, eliminating user error and confusion.

El sistema **genera automáticamente** los comandos apropiados con **parámetros pre-llenados** basados en el contexto actual, eliminando errores del usuario y confusión.

**Smart Parameter Injection / Inyección Inteligente de Parámetros:**
- Trade IDs are automatically inserted
- Player names are automatically detected
- Language preferences are automatically applied
- Current trade state determines available actions

#### **3. GUI-First, Commands-Second Approach / Enfoque GUI-Primero, Comandos-Segundo**
While traditional plugins rely heavily on command memorization, this system treats commands as **background executors** for **GUI interactions** and **clickable text**.

Mientras que los plugins tradicionales dependen mucho de la memorización de comandos, este sistema trata los comandos como **ejecutores de fondo** para **interacciones GUI** y **texto clickeable**.

### **🔄 Real-World Usage Examples / Ejemplos de Uso del Mundo Real**

#### **Traditional vs. Our Approach / Tradicional vs. Nuestro Enfoque**

**❌ Traditional Plugin Workflow:**
```
1. Player types: /trade Steve
2. Opens GUI, selects items
3. Player must remember: /tradeconfirm Steve 12345
4. Other player must remember: /traderesponse accept 12345
5. Both must remember: /tradefinalize or similar
```

**✅ Our Clickable Workflow:**
```
1. Player types: /trade Steve (only initial command needed)
2. Opens GUI, selects items, clicks [CONFIRM] button
3. Steve receives: "📬 PlayerName sent you a trade! [CLICK TO VIEW]"
4. Steve clicks, adds items, clicks [CONFIRM] button
5. Both receive: "Trade ready! [CLICK TO COMPLETE]"
```

#### **Benefits of This Design / Beneficios de Este Diseño**

1. **🧠 Reduced Cognitive Load / Carga Cognitiva Reducida**
   - Players don't need to memorize trade IDs
   - No need to remember complex command syntax
   - Context is always provided visually

2. **⚡ Faster Interactions / Interacciones Más Rápidas**
   - Single click vs. typing long commands
   - No typing errors or parameter mistakes
   - Immediate visual feedback

3. **🎮 Better User Experience / Mejor Experiencia de Usuario**
   - Intuitive GUI-driven workflow
   - Mobile-friendly (easier on phones/tablets)
   - Accessible to players of all skill levels

4. **🔒 Error Prevention / Prevención de Errores**
   - Impossible to use wrong trade IDs
   - Automatic parameter validation
   - Context-aware action availability

### **🛠️ Technical Implementation / Implementación Técnica**

The system uses **Adventure Components** with **click events** that automatically execute the appropriate commands with the correct parameters:

El sistema utiliza **Adventure Components** con **eventos de click** que ejecutan automáticamente los comandos apropiados con los parámetros correctos:

```java
// Example of auto-generated clickable text
Component.text("📦 CLICK TO VIEW ITEMS")
    .clickEvent(ClickEvent.runCommand("/traderesponse accept " + tradeId))
    .hoverEvent(HoverEvent.showText("Accept trade #" + tradeId))
```

### **📝 Summary / Resumen**

The "unusual" command structure is actually a **carefully designed user experience enhancement** that transforms traditional command-based interactions into intuitive, clickable workflows. Players interact primarily through **GUIs** and **clickable messages**, while commands work behind the scenes to provide seamless functionality.

La estructura de comandos "extraña" es en realidad una **mejora cuidadosamente diseñada de la experiencia del usuario** que transforma las interacciones tradicionales basadas en comandos en flujos de trabajo intuitivos y clickeables. Los jugadores interactúan principalmente a través de **GUIs** y **mensajes clickeables**, mientras que los comandos funcionan detrás de escenas para proporcionar funcionalidad fluida.
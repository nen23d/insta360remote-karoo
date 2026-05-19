# Insta360 GO Ultra — Extensión para Hammerhead Karoo 3

Control de cámara Insta360 GO Ultra directamente desde el Karoo 3 vía Bluetooth BLE.

## Funcionalidades

- **Start / Stop grabación** desde el Karoo
- **Tomar fotos** con un toque
- **Marcar highlights** durante el ride
- **Selección de modo**: Vídeo, Foto, Timelapse
- **Auto-grabación**: inicia/para automáticamente con el ride
- **Campo de datos "Cámara Estado"**: añadible a cualquier página de perfil
- **Campo de datos "Cámara Batería"**: porcentaje de batería en tiempo real
- **Servicio foreground**: mantiene la conexión BLE aunque el Karoo pase la app al background

## Protocolo BLE

La extensión emula el **"Insta360 GPS Remote"** para que la cámara acepte la conexión:

- **Nombre de anuncio**: `Insta360 GPS Remote`  
- **Protocolo**: GATT + Protocol Buffers  
- **Servicios GATT**: `0x0000AA01` (control) / `0x0000AA02` (estado)  
- Basado en ingeniería inversa documentada por la comunidad  
  - https://medium.com/@patrickchwalek/ble-control-of-insta360-cameras  
  - https://github.com/RigacciOrg/insta360-wifi-api

> ⚠️ Los UUIDs de servicios GATT y los payloads protobuf de la GO Ultra
> específicamente pueden requerir ajuste mediante sniffing BLE de tu dispositivo.
> Ver sección "Calibración BLE" más abajo.

## Requisitos

- **Android Studio** Hedgehog (2023.1) o superior
- **JDK 17**
- Cuenta GitHub para acceder a `karoo-ext` (GitHub Packages)
- **Karoo 3** con firmware 1.524 o superior
- **Insta360 GO Ultra** con firmware actualizado
- App **Hammerhead Companion** en el móvil (para sideloading)

## Compilación

### 1. Clonar y configurar credenciales

```bash
git clone <este-repo>
cd karoo-insta360
```

Crea el archivo `~/.gradle/gradle.properties` (o `gradle.properties` en la raíz del proyecto):

```properties
gpr.user=TU_USUARIO_GITHUB
gpr.key=TU_GITHUB_PERSONAL_ACCESS_TOKEN
```

El token necesita el scope `read:packages`.

### 2. Compilar

```bash
./gradlew assembleRelease
```

El APK se genera en:
```
app/build/outputs/apk/release/app-release.apk
```

### 3. Instalar en el Karoo (sideloading)

1. Abre la página de releases en el navegador de tu móvil
2. **Mantén pulsado** el enlace al APK
3. Selecciona **"Compartir"** → **Hammerhead Companion**
4. El Karoo mostrará una pantalla de instalación

### 4. Primer uso

1. Abre la app **"Insta360 Control"** desde el menú del Karoo
2. Pulsa **"BUSCAR CÁMARA"** — asegúrate de que la GO Ultra está encendida
3. La extensión se anuncia como `Insta360 GPS Remote` para que la cámara la acepte
4. Una vez conectado, los campos de datos estarán disponibles en el editor de perfiles

## Añadir campos al perfil

1. Menú → **Perfiles** → editar perfil
2. Añadir página de datos
3. Buscar **"Cámara Estado"** o **"Cámara Batería"** en la lista de campos
4. El campo aparece en la sección **"Insta360 GO Ultra"**

## Calibración BLE (avanzado)

Si la cámara no responde a los comandos, los UUIDs o payloads protobuf pueden
diferir en tu versión de firmware. Procedimiento de calibración:

### Opción A: nRF Connect (Android/iOS)
1. Conecta tu móvil a la GO Ultra con la app oficial de Insta360
2. Abre **nRF Connect** simultáneamente
3. En "GATT Server", observa los servicios y características activos
4. Actualiza `Insta360BleConstants.kt` con los UUIDs correctos

### Opción B: Wireshark + HCI snoop log
```bash
# En Android, activar HCI snoop log en opciones de desarrollador
# El log se guarda en /sdcard/btsnoop_hci.log
adb pull /sdcard/btsnoop_hci.log
# Abrir en Wireshark, filtrar: btatt
```

### Opción C: APK de Insta360 decompilado
```bash
# Extraer protobuf definitions del APK oficial
apktool d Insta360.apk
grep -r "UUID\|SERVICE\|CHARACTERISTIC" ./smali/
```

## Estructura del proyecto

```
app/src/main/java/io/karoo/insta360/
├── Insta360App.kt                    # Application class
├── ble/
│   ├── Insta360BleConstants.kt       # UUIDs, comandos protobuf
│   ├── Insta360BleManager.kt         # Gestión de conexión BLE
│   └── Insta360BleService.kt         # Servicio foreground
├── extension/
│   └── CameraStatusDataType.kt       # Campos de datos para Karoo
└── ui/
    └── MainActivity.kt               # Pantalla de configuración
```

## Contribuir

Este es el **primer intento** de integrar Insta360 con Karoo. Las contribuciones
son muy bienvenidas, especialmente:

- Confirmación / corrección de UUIDs GATT para GO Ultra
- Decoding completo de respuestas protobuf
- Soporte para más modelos Insta360
- Tests en hardware real

## Licencia

MIT — úsalo, modifícalo y compártelo libremente.

## Créditos

- Protocolo BLE: [@patrickchwalek](https://medium.com/@patrickchwalek)
- API WiFi: [RigacciOrg/insta360-wifi-api](https://github.com/RigacciOrg/insta360-wifi-api)
- Karoo SDK: [hammerheadnav/karoo-ext](https://github.com/hammerheadnav/karoo-ext)
- Inspiración: [ClipRide (GoPro para Karoo)](https://github.com/yrkan/clipride)

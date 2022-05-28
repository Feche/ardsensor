#include <EEPROM.h>
#include <NeoSWSerial.h>

NeoSWSerial BT(2, 3);

#define _VERSION                  "ArdSensor-1.0.1"
#define _ROOT_TIMEOUT             5
#define _ENABLE_PROTECTIONS

#define PIN_COMPRESSOR            A4
#define PIN_SENSOR                A5
#define PIN_LED                   13
#define PIN_FAN                   A3

#define SENSOR_MAX_PSI            170
#define SENSOR_MIN_PSI            80
#define SENSOR_MAX_ON_TIME        10
#define SENSOR_MIN_ON_TIME        1
#define SENSOR_MAX_OFF_TIME       20
#define SENSOR_MIN_OFF_TIME       1

#define EEPROM_PSI_ON             0
#define EEPROM_PSI_OFF            1
#define EEPROM_C_MODE             2
#define EEPROM_ON_TIME            3
#define EEPROM_OFF_TIME           4
#define EEPROM_MIN_VOLTAGE        5
#define EEPROM_MAX_VOLTAGE        9
#define EEPROM_CALIBRATION_PSI    13
#define EEPROM_PASSWORD           17

#define DEFAULT_PSI_ON            90
#define DEFAULT_PSI_OFF           130
#define DEFAULT_C_MODE            1
#define DEFAULT_ON_TIME           2
#define DEFAULT_OFF_TIME          4
#define DEFAULT_MIN_VOLTAGE       0.45 // 0.49
#define DEFAULT_MAX_VOLTAGE       2.20 // 2.18
#define DEFAULT_CALIBRATION_PSI   100

#define ALERT_NONE                0
#define ALERT_NO_PRESSURE         1
#define ALERT_MAX_PSI             2
#define ALERT_NO_SENSOR           3

#define MODE_MANUAL               0
#define MODE_AUTO                 1

struct gSensor
{
    char  gRx[100]  = { '\0' };
    char  gTmp[100] = { '\0' };
    int   gIdx      = 0;
    float gTick     = millis();
    float gMin_Voltage;
    float gMax_Voltage;
    int   gPassword;
    int   gCalibration_Psi;
    int   gCalibration_Step;
    bool  gCompressor_On;
    float gCompressor_On_Tick;
    bool  gCompressor_Cooldown;
    float gCompressor_CooldownTick;
    int   gCompressor_Alert;
    int   gCompressor_StartPsi;
    float gCompressor_Time_Used;
    float gCompressor_Off_Tick;
    bool  gRoot = false;
    char* gRoot_Pwd = "Nadie_La_Sabe";
    long  gRoot_Tick;
    bool  gRoot_Debug = false;
    bool  gLogged = false;
    float gLogged_Tick;
    bool  gLoggingIn;
    float gLoggingIn_Tick;
};

struct gSettings
{
    int gPsi_On;
    int gPsi_Off;
    int gC_Mode;
    int gOn_Time;
    int gOff_Time;
};

gSensor   gSensor;
gSettings gSettings;

void setup() 
{      
    Serial.begin(9600);
    BT.begin(19200);

    pinMode(PIN_COMPRESSOR, OUTPUT);
    pinMode(PIN_SENSOR, INPUT);
    pinMode(PIN_LED, OUTPUT);

    gSettings.gPsi_On   = EEPROM.read(EEPROM_PSI_ON);
    gSettings.gPsi_Off  = EEPROM.read(EEPROM_PSI_OFF);
    gSettings.gC_Mode   = EEPROM.read(EEPROM_C_MODE);
    gSettings.gOn_Time  = EEPROM.read(EEPROM_ON_TIME);
    gSettings.gOff_Time = EEPROM.read(EEPROM_OFF_TIME);

    if(gSettings.gPsi_On == 255 || gSettings.gPsi_Off == 255 || gSettings.gC_Mode == 255 || gSettings.gOn_Time == 255 || gSettings.gOff_Time == 255)
    {
        gSettings.gPsi_On   = DEFAULT_PSI_ON;
        gSettings.gPsi_Off  = DEFAULT_PSI_OFF;
        gSettings.gC_Mode   = DEFAULT_C_MODE;
        gSettings.gOn_Time  = DEFAULT_ON_TIME;
        gSettings.gOff_Time = DEFAULT_OFF_TIME;

        EEPROM.update(EEPROM_PSI_ON   , gSettings.gPsi_On);
        EEPROM.update(EEPROM_PSI_OFF  , gSettings.gPsi_Off);
        EEPROM.update(EEPROM_C_MODE   , gSettings.gC_Mode);
        EEPROM.update(EEPROM_ON_TIME  , gSettings.gOn_Time);
        EEPROM.update(EEPROM_OFF_TIME , gSettings.gOff_Time);
    }

    EEPROM.get(EEPROM_MIN_VOLTAGE     , gSensor.gMin_Voltage);
    EEPROM.get(EEPROM_MAX_VOLTAGE     , gSensor.gMax_Voltage);
    EEPROM.get(EEPROM_CALIBRATION_PSI , gSensor.gCalibration_Psi);
    EEPROM.get(EEPROM_PASSWORD        , gSensor.gPassword);

    if(isnan(gSensor.gMin_Voltage) || isnan(gSensor.gMax_Voltage) || isnan(gSensor.gCalibration_Psi))
    {
        gSensor.gMin_Voltage      = DEFAULT_MIN_VOLTAGE;
        gSensor.gMax_Voltage      = DEFAULT_MAX_VOLTAGE;
        gSensor.gCalibration_Psi  = DEFAULT_CALIBRATION_PSI;

        EEPROM.put(EEPROM_MIN_VOLTAGE     , gSensor.gMin_Voltage);
        EEPROM.put(EEPROM_MAX_VOLTAGE     , gSensor.gMax_Voltage);
        EEPROM.put(EEPROM_CALIBRATION_PSI , gSensor.gCalibration_Psi);
    }

    delay(1000);
    
    Serial.print(F("Device "));
    Serial.print(_VERSION);
    Serial.println(F(" started"));
}

void loop() 
{
    if(millis() - gSensor.gTick >= 500)
    {
        gSensor.gTick = millis();
        digitalWrite(PIN_LED, !digitalRead(PIN_LED));
        
        checkCompressor();
        checkRootTimeout();
        checkLogged();
    }

    while(BT.available())
    {
        byte b = BT.read();
        if(b == '\n')
        {
            onReceiveBluetoothData(gSensor.gRx);
            memset(gSensor.gRx, '\0', sizeof(gSensor.gRx));
            gSensor.gIdx = 0;
            break;
        }
        else
        {  
            gSensor.gRx[gSensor.gIdx] = b;
            gSensor.gIdx++;
        }
    }

    while(Serial.available())
    {
        byte b = Serial.read();
       
        if(b == '\n')
        {
            onReceiveRootCommand(gSensor.gRx);
            memset(gSensor.gRx, '\0', sizeof(gSensor.gRx));
            gSensor.gIdx = 0;
            break;
        }
        else
        {  
            gSensor.gRx[gSensor.gIdx] = b;
            gSensor.gIdx++;
        }
    }
}

void onReceiveBluetoothData(char* data)
{
    if(gSensor.gRoot_Debug)
    {
        Debug("Received: ");
        Debugln(data);
    }

    if(strcmp("default", data) == 0)
    {
        for(int i = 0; i < 1024; i++)
          EEPROM.update(i, 0xFF);
          
        BT.println(F("Dispositivo reiniciado a fabrica."));
        delay(1000);

        void(* resetFunc) (void) = 0;
        resetFunc();
    }
    else if(strcmp("calibrate", data) == 0)
    {
        if(gSensor.gCompressor_Alert != 0 && gSensor.gCompressor_Alert != ALERT_MAX_PSI)
        {
            BT.println(F("La central se encuentra con alarmas activas, abortando calibración.."));
            return;    
        }
        
        BT.println(F("Por favor, vaciar el tanque de aire [0 PSI] y escribir OK"));
        gSensor.gCalibration_Step = 1;
        gSettings.gC_Mode = MODE_MANUAL;
        setCompressorStatus(false);
    }
    else if(strcmp("OK", data) == 0 || strcmp("ok", data) == 0)
    {
        float volts = getAnalogVoltage(PIN_SENSOR);
        if(gSensor.gCalibration_Step == 1)
        {
            gSensor.gMin_Voltage = volts;
            gSensor.gCalibration_Step = 2;

            BT.print(F("El compresor se encenderá en 10 segundos, cuando la presión llegue a ["));
            BT.print(gSensor.gCalibration_Psi);
            BT.println(F("PSI] escriba OK y el compresor se apagará"));
            
            BT.print(F("ATENCIÓN: EL TANQUE TIENE QUE ESTAR EN ["));
            BT.print(gSensor.gCalibration_Psi);
            BT.println(F("PSI] DE LO CONTRARIO, TENDRÁ QUE VOLVER A CALIBRAR EL SENSOR."));

            delay(10000);

            setCompressorStatus(true);
            BT.println(F("COMPRESOR ENCENDIDO"));
        }
        else if(gSensor.gCalibration_Step == 2)
        {
            gSensor.gMax_Voltage = volts;
            gSensor.gCalibration_Step = 0;

            EEPROM.put(EEPROM_MIN_VOLTAGE, gSensor.gMin_Voltage);
            EEPROM.put(EEPROM_MAX_VOLTAGE, gSensor.gMax_Voltage);

            BT.print(F("gMin_Voltage = "));
            BT.print(gSensor.gMin_Voltage);
            BT.print(F(", gMax_Voltage = "));
            BT.println(gSensor.gMax_Voltage);
            BT.println(F("CALIBRACIÓN GUARDADA"));

            setCompressorStatus(false);
        }
    }
    
    // -------------------------------------- //
    else if(strstr(data, ",register"))
    {
        char* pwd = strtok(data, ",");
        gSensor.gPassword = atoi(pwd);
        EEPROM.put(EEPROM_PASSWORD, gSensor.gPassword);

        sendData("register_ok");
        gSensor.gLogged = true;
        Debugln("Logged in");
    }
    else if(strstr(data, ",login"))
    {
        char* str = strtok(data, ",");
        int pwd = atoi(str);
        sendData(pwd == gSensor.gPassword ? "login_ok" : "login_fail");

        if(pwd == gSensor.gPassword)
        {
            gSensor.gLogged = true;
            Debugln("Logged in");
        }
    }
    else if(strcmp("start", data) == 0)
    {
        sprintf(gSensor.gTmp, "%d,%d,%d,%d,%d,start", gSettings.gC_Mode, gSettings.gPsi_On, gSettings.gPsi_Off, gSettings.gOn_Time, gSettings.gOff_Time);
        sendData(gSensor.gTmp);
    }
    else if(strcmp("get", data) == 0)
    {
        int seconds = (gSettings.gOff_Time * 60) - ((millis() - gSensor.gCompressor_CooldownTick) / 1000);
        sprintf(gSensor.gTmp, "%d,%d,%d,%d,%d,psi", getTankPressure(), gSettings.gPsi_Off, gSensor.gCompressor_On ? 1 : 0, gSensor.gCompressor_Cooldown ? seconds : 0, gSensor.gCompressor_Alert);
        sendData(gSensor.gTmp);

        if(!gSensor.gLogged)
        {
            if(!gSensor.gLoggingIn)
            {
                sendData(gSensor.gPassword == -1 ? "register_start" : "login_start");
                gSensor.gLoggingIn = true;
            }
            gSensor.gLoggingIn_Tick = millis();
        }
        else
        {
            gSensor.gLogged_Tick = millis();
            gSensor.gLoggingIn = false;
        }
    }
    else if(strcmp("comp_auto", data) == 0)
    {
        sendData("comp_auto");
        setCompressorStatus(false);
        gSettings.gC_Mode = MODE_AUTO;
        EEPROM.update(EEPROM_C_MODE, gSettings.gC_Mode);
        checkCompressor();
    }
    else if(strcmp("comp_manual", data) == 0)
    {
        sendData("comp_manual");
        setCompressorStatus(false);
        gSettings.gC_Mode = MODE_MANUAL;
        gSensor.gCompressor_Alert = ALERT_NONE;
        EEPROM.update(EEPROM_C_MODE, gSettings.gC_Mode);
        checkCompressor();
    }
    else if(strcmp("comp_on", data) == 0)
    {
        if(gSettings.gC_Mode != MODE_MANUAL) return;
        setCompressorStatus(true);
        sendData("Compresor: encendido.,not");
    }
    else if(strcmp("comp_off", data) == 0)
    {
        if(gSettings.gC_Mode != MODE_MANUAL) return;
        setCompressorStatus(false);
        sendData("Compresor: apagado.,not");
    }
    else if(strstr(data, ",save"))
    {
        char* psiOn     = strtok(data, ",");
        char* psiOff    = strtok(NULL, ",");
        char* onTime    = strtok(NULL, ",");
        char* offTime   = strtok(NULL, ",");
        char* cMode     = strtok(NULL, ",");

        psiOn   = atoi(psiOn);
        psiOff  = atoi(psiOff);
        onTime  = atoi(onTime);
        offTime = atoi(offTime);
        cMode   = atoi(cMode);

        if(psiOn == psiOff)
        {
            sendData("La presión de encendido no puede ser igual a la de apagado.,not");
            return;
        }
        else if(psiOff > SENSOR_MAX_PSI)
        {
            sprintf(gSensor.gTmp, "La presión maxima de apagado es %d psi.,not", SENSOR_MAX_PSI);
            sendData(gSensor.gTmp);
            return;
        }
        else if(psiOn < SENSOR_MIN_PSI)
        {
            sprintf(gSensor.gTmp, "La presión minima de encendido es %d psi.,not", SENSOR_MIN_PSI);
            sendData(gSensor.gTmp);
            return;
        }
        else if(onTime > SENSOR_MAX_ON_TIME)
        {
            sprintf(gSensor.gTmp, "El tiempo maximo de funcionamiento es de %d minutos.,not", SENSOR_MAX_ON_TIME);
            sendData(gSensor.gTmp);
            return;
        }
        else if(onTime < SENSOR_MIN_ON_TIME)
        {
            sprintf(gSensor.gTmp, "El tiempo minimo de funcionamiento es de %d minuto(s).,not", SENSOR_MIN_ON_TIME);
            sendData(gSensor.gTmp);
            return;
        }
        else if(offTime > SENSOR_MAX_OFF_TIME)
        {
            sprintf(gSensor.gTmp, "El tiempo maximo de enfriamiento es de %d minutos.,not", SENSOR_MAX_OFF_TIME);
            sendData(gSensor.gTmp);
            return;
        }
        else if(offTime < SENSOR_MIN_OFF_TIME)
        {
            sprintf(gSensor.gTmp, "El tiempo minimo de enfriamiento es de %d minuto(s).,not", SENSOR_MIN_OFF_TIME);
            sendData(gSensor.gTmp);
            return;
        }

        gSettings.gPsi_On   = psiOn;
        gSettings.gPsi_Off  = psiOff;
        gSettings.gOn_Time  = onTime;
        gSettings.gOff_Time = offTime;
        gSettings.gC_Mode   = cMode;

        EEPROM.update(EEPROM_PSI_ON   , gSettings.gPsi_On);
        EEPROM.update(EEPROM_PSI_OFF  , gSettings.gPsi_Off);
        EEPROM.update(EEPROM_ON_TIME  , gSettings.gOn_Time);
        EEPROM.update(EEPROM_OFF_TIME , gSettings.gOff_Time);
        EEPROM.update(EEPROM_C_MODE   , gSettings.gC_Mode);

        sendData("Datos guardados.,not");
    }
}

void onReceiveRootCommand(char* data)
{
    if(strcmp(data, gSensor.gRoot_Pwd) == 0)
    {
        gSensor.gRoot_Tick = millis();
        gSensor.gRoot = true;
        
        sprintf(gSensor.gTmp, "Timeout in %d minute(s).", _ROOT_TIMEOUT);
        Serial.println(gSensor.gTmp);
        return;
    }

    if(!gSensor.gRoot) return;
    
    if(strcmp("debug", data) == 0)
    {
        gSensor.gRoot_Debug = !gSensor.gRoot_Debug;
        Debug("gSensor.gDebug = ");
        Debugln(gSensor.gRoot_Debug ? "true" : "false");
    }
    else if(strcmp("version", data) == 0)
    {
        Debugln(_VERSION);
    }
    else if(strcmp("config", data) == 0)
    {
        sprintf(gSensor.gTmp, "gPsi_On = %d, gPsi_Off = %d, gC_Mode = %d, gOn_Time = %d, gOff_Time = %d, gPassword = %d", gSettings.gPsi_On, gSettings.gPsi_Off, gSettings.gC_Mode, gSettings.gOn_Time, gSettings.gOff_Time, gSensor.gPassword);
        Debugln(gSensor.gTmp);
        Serial.print(F("gMin_Voltage = "));
        Serial.print(gSensor.gMin_Voltage);
        Serial.print(F(", "));
        Serial.print(F("gMax_Voltage = "));
        Serial.print(gSensor.gMax_Voltage);
        Serial.print(", ");
        Serial.print(F("gCalibration_Psi = "));
        Serial.println(gSensor.gCalibration_Psi);
    }
    else if(strcmp("voltage", data) == 0)
    {
        float volts = getAnalogVoltage(PIN_SENSOR);
        Serial.print(F("Tank sensor voltage: "));
        Serial.print(volts);
        Serial.println(F(" volts"));
    }
    else if(strcmp("default", data) == 0)
    {
        for(int i = 0; i < 1024; i++)
          EEPROM.update(i, 0xFF);
          
        Serial.println(F("Dispositivo reiniciado a fabrica."));
        delay(1000);

        void(* resetFunc) (void) = 0;
        resetFunc();
    }
    else if(strcmp("calibrate", data) == 0)
    {
        if(gSensor.gCompressor_Alert != 0 && gSensor.gCompressor_Alert != ALERT_MAX_PSI)
        {
            Serial.println(F("La central se encuentra con alarmas activas, abortando calibración.."));
            return;    
        }
        
        Serial.println(F("Por favor, vaciar el tanque de aire [0 PSI] y escribir OK"));
        gSensor.gCalibration_Step = 1;
        gSettings.gC_Mode = MODE_MANUAL;
        setCompressorStatus(false);
    }
    else if(strcmp("OK", data) == 0 || strcmp("ok", data) == 0)
    {
        float volts = getAnalogVoltage(PIN_SENSOR);
        if(gSensor.gCalibration_Step == 1)
        {
            gSensor.gMin_Voltage = volts;
            gSensor.gCalibration_Step = 2;

            Serial.print(F("El compresor se encenderá en 10 segundos, cuando la presión llegue a ["));
            Serial.print(gSensor.gCalibration_Psi);
            Serial.println(F("PSI] escriba OK y el compresor se apagará"));
            
            Serial.print(F("ATENCIÓN: EL TANQUE TIENE QUE ESTAR EN ["));
            Serial.print(gSensor.gCalibration_Psi);
            Serial.println(F("PSI] DE LO CONTRARIO, TENDRÁ QUE VOLVER A CALIBRAR EL SENSOR."));

            delay(10000);

            setCompressorStatus(true);
            Serial.println(F("COMPRESOR ENCENDIDO"));
        }
        else if(gSensor.gCalibration_Step == 2)
        {
            gSensor.gMax_Voltage = volts;
            gSensor.gCalibration_Step = 0;

            EEPROM.put(EEPROM_MIN_VOLTAGE, gSensor.gMin_Voltage);
            EEPROM.put(EEPROM_MAX_VOLTAGE, gSensor.gMax_Voltage);

            Serial.print(F("gMin_Voltage = "));
            Serial.print(gSensor.gMin_Voltage);
            Serial.print(F(", gMax_Voltage = "));
            Serial.println(gSensor.gMax_Voltage);
            Serial.println(F("CALIBRACIÓN GUARDADA"));

            setCompressorStatus(false);
        }
    }
}

void checkCompressor()
{
    if(gSettings.gC_Mode == MODE_MANUAL || gSensor.gCompressor_Alert != ALERT_NONE) return;
    
    int psi = getTankPressure();

    // If on cooldown, do nothing and also check if cooldown is done
    if(gSensor.gCompressor_Cooldown)
    {
        if(millis() - gSensor.gCompressor_CooldownTick >= gSettings.gOff_Time * 60000)
        {
            gSensor.gCompressor_Cooldown = false;
            gSensor.gCompressor_On_Tick = 0;
        }
        return;
    }

    //
    // ALERT DETECTION
    //

    #ifdef _ENABLE_PROTECTIONS

        //
        // No sensor connected [ERR44]
        //
        
        if(getAnalogVoltage(PIN_SENSOR) <= 0.40)
        {
            gSensor.gCompressor_Alert = ALERT_NO_SENSOR;
            setCompressorStatus(false);
            return;
        }
        else if(gSensor.gCompressor_Alert == ALERT_NO_SENSOR)
        {
            gSensor.gCompressor_Alert = ALERT_NONE;
        }

        //
        // Compressor & battery protection [ERR42]
        //
        
        if(gSensor.gCompressor_On_Tick > 0 && millis() - gSensor.gCompressor_On_Tick >= 45000 && psi < (gSensor.gCompressor_StartPsi + 10))
        {
            gSensor.gCompressor_Alert = ALERT_NO_PRESSURE;
            setCompressorStatus(false);
            return;
        }

        //
        // Max psi alert [ERR40]
        //
        
        if(psi >= (SENSOR_MAX_PSI + 5))
        {
            gSensor.gCompressor_Alert = ALERT_MAX_PSI;
            setCompressorStatus(false);
            return;
        }

    #endif

    //
    // COMPRESSOR PRESSURE DETECTION
    //
    
    if(psi <= gSettings.gPsi_On && !getCompressorStatus())
    {
        gSensor.gCompressor_On_Tick = millis();
        gSensor.gCompressor_StartPsi = psi;
        setCompressorStatus(true);

        if(millis() - gSensor.gCompressor_Off_Tick < gSettings.gOff_Time * 60000)
        {
            gSensor.gCompressor_On_Tick = gSensor.gCompressor_On_Tick - (gSensor.gCompressor_Time_Used * 1000);
        }
    }
    else if(psi >= (gSettings.gPsi_Off + 4) && getCompressorStatus())
    {
        setCompressorStatus(false);
        gSensor.gCompressor_Time_Used = millis() - gSensor.gCompressor_On_Tick;
        gSensor.gCompressor_Off_Tick = millis();
        gSensor.gCompressor_On_Tick = 0;
    }

    //
    // Check and start cooldown if needed
    //
    
    if(gSensor.gCompressor_On_Tick > 0 && millis() - gSensor.gCompressor_On_Tick >= (gSettings.gOn_Time * 60000) && !gSensor.gCompressor_Cooldown)
    {
        gSensor.gCompressor_Cooldown = true;
        gSensor.gCompressor_Time_Used = 0;
        setCompressorStatus(false);
        gSensor.gCompressor_CooldownTick = millis();
    }
}

void checkRootTimeout()
{
    if(gSensor.gRoot && millis() - gSensor.gRoot_Tick >= _ROOT_TIMEOUT * 60000)
    {
        gSensor.gRoot = false;
    }
}

void checkLogged()
{
    if(millis() - gSensor.gLogged_Tick > 2000 && gSensor.gLogged)
    {
        gSensor.gLogged = false;
        Debugln("Logged out");
    }

    if(millis() - gSensor.gLoggingIn_Tick > 2000 && gSensor.gLoggingIn)
    {
        gSensor.gLoggingIn = false;
    }
}

// -------------------------- //

void sendData(char* data)
{
    BT.println(data);

    if(gSensor.gRoot_Debug)
    {
        Debug("Sending: ");
        Debugln(data);
    }
}

int getTankPressure()
{
    float volts = getAnalogVoltage(PIN_SENSOR);
    volts = volts - gSensor.gMin_Voltage;
    return (volts / (gSensor.gMax_Voltage - gSensor.gMin_Voltage)) * gSensor.gCalibration_Psi;
}

float getAnalogVoltage(int pin)
{
    float voltage;
    for(int i = 0; i < 100; i++)
        voltage = voltage + analogRead(PIN_SENSOR);

    voltage = voltage / 100;
    return (voltage / 1023.0) * 5.0;
}

void setCompressorStatus(bool st)
{
    digitalWrite(PIN_COMPRESSOR, st ? HIGH : LOW);
    gSensor.gCompressor_On = st;
    gSensor.gCompressor_On_Tick = st ? gSensor.gCompressor_On_Tick : 0;
}

bool getCompressorStatus()
{
    return gSensor.gCompressor_On;
}

void Debug(char* str)
{
    if(gSensor.gRoot)
        Serial.print(str);
}

void Debugln(char* str)
{
    if(gSensor.gRoot)
        Serial.println(str);
}

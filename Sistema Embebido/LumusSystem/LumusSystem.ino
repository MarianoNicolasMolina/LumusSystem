#include <AccelStepper.h>
#include <Wire.h>
#include <RTClib.h>

#define MICROFONO_PIN A0
#define RTC_SCL_PIN 21
#define RTC_SDA_PIN 20
#define REED_SWITCH_DOWN_PIN 10
#define REED_SWITCH_UP_PIN 9
#define LDR_PIN 6
#define LED_PIN 22
#define BUZZER_PIN 7
#define PIR_PIN 8
#define BUTTON_ALARMA_PIN 11
#define BUTTON_LIGHT_PIN 12
#define MOTOR_PIN1 2
#define MOTOR_PIN2 3
#define MOTOR_PIN3 4
#define MOTOR_PIN4 5
#define APLAUSO 80
#define DESV_MISMO_APL 200
#define BRECHA_SEG_APLAU 500
#define DESVIO_3_APLAUSOS 1000
#define TIEMPO_ESPERA_LDR 3000
#define TIEMPO_LECTURA_HORA 30000 // cada 30 segundos
#define TIEMPO_SENSORES 1000 // cada un segundo
#define PAUSA_PIR 6000
#define DELAY_BOTON 70
#define INTERVALO_ALARMA 200
#define INTERVALO_SEND_BT 1000
//Seteo el motor paso a paso para la persiana

AccelStepper motorPAP(4, MOTOR_PIN1, MOTOR_PIN3, MOTOR_PIN2, MOTOR_PIN4);
// registros de tiempo

unsigned long previousMilisLDR = 0;
unsigned long tiempoUltAplauso = 0;
unsigned long tiempoAplausoAct = 0;
unsigned long ultCbioLuz = 0;
unsigned long previusMillisPIR = 0;        
unsigned long ultLecturaBotonAlarma = 0;
unsigned long ultLecturaBotonLuz = 0;
unsigned long previousMilisAlarma = 0;
unsigned long previousMilisSendBT = 0;


int ultValorSonido;
int ultValorBotonLuz = HIGH;
int ultValorBotonAlarma = HIGH;
int botonLuzEstado;
int botonAlarmaEstado;
int estadoBuzzer = LOW;
int arrayAlarmasHora[5];
int arrayAlarmasMinutos[5];

//banderas
bool estadoLuzLDR = false; 
bool bandTimerLDR =false ;
bool estadoLuz = false;
bool lockLow = true;
bool takeLowTime;  
bool estadoAlarma = false;
bool posicionPersiana = false;
bool ldrHabilitado = true;
bool pirHabilitado = true;
bool moviendoPersiana = false;


RTC_DS1307 RTC;

void setup(){

  pinMode(LED_PIN, OUTPUT);
  Wire.begin(); // Inicia el puerto I2C
  RTC.begin(); // Inicia la comunicación con el RTC
  RTC.adjust(DateTime(__DATE__, __TIME__)); // Establece la fecha y hora (Comentar una vez establecida la hora)
  
  pinMode(BUZZER_PIN, OUTPUT);
  for(int i=0 ; i < 5 ; i++){
    arrayAlarmasHora[i] = 99;
    arrayAlarmasMinutos[i] = 99;
  }
  pinMode(REED_SWITCH_UP_PIN, INPUT);
  pinMode(REED_SWITCH_DOWN_PIN,INPUT);
  pinMode(LDR_PIN, INPUT);
  pinMode(PIR_PIN, INPUT);
  digitalWrite(BUZZER_PIN,HIGH);
  motorPAP.setSpeed(500);
  motorPAP.setAcceleration(300.0); 
  motorPAP.setMaxSpeed(800.0);   
  pinMode(BUTTON_ALARMA_PIN, INPUT_PULLUP);
  pinMode(BUTTON_LIGHT_PIN, INPUT_PULLUP);
  Serial.begin(9600);


}

void loop(){
  clapOn();
  if(ldrHabilitado){
    sensorLDR(estadoLuzLDR ? false : true);
    
  }
  if(pirHabilitado){
    sensorPIR();
  }
  pulsadorLuz();
  pulsadorAlarma();
  enviarInfoBluetooth();
  checkAlarmas();

  if(estadoAlarma == true){
    buzz();
  }
  if(Serial.available()){
    String dato = Serial.readString();
    recibirBluetooth(dato,false);
  }
  

}

//Funcion de Subido o Bajado de persiana
void persiana(bool direccion) {
  if(direccion){
    motorPAP.moveTo(16384);//4096 es una revolucion si quiero que continue por mas tiempo multiplico ese numero
  }else{
    motorPAP.moveTo(-16384);
  }
  do {
    moviendoPersiana = true;
    clapOn();
    pulsadorLuz();
    pulsadorAlarma();
    if(estadoAlarma == true){
      buzz();
    }
    motorPAP.run(); 
  } while (digitalRead(direccion ? REED_SWITCH_DOWN_PIN : REED_SWITCH_UP_PIN) == LOW);
  moviendoPersiana = false;
  
}

void pulsadorLuz(){
  int lecturaPulsador = digitalRead(BUTTON_LIGHT_PIN);
  //Si se cambio el estado ya sea por un ruido o por haber pulsado
  if (lecturaPulsador != ultValorBotonLuz) {
    // reseteo el tiempo de delay
    ultLecturaBotonLuz = millis();
  }

  if ((millis() - ultLecturaBotonLuz) > DELAY_BOTON) {
    // en este punto se supone que el boton fue presionado 
    // por mas tiempo que el delay

    // hago chequeo del cambio de estado
    if (lecturaPulsador != botonLuzEstado) {
      botonLuzEstado = lecturaPulsador;

      // si la lectura es LOW quiere decir que se presiono
      if (botonLuzEstado == LOW) {
        estadoLuz = !estadoLuz;
        cambioEstadoLuz(estadoLuz);
      }
    }
  }
  // no importa si el led estaba prendido o no o si se presiono el boton o no
  // Si no cambio se vuelve a escribir el mismo estado
      
  // salvo la lectura para la proxima vuelta
  ultValorBotonLuz = lecturaPulsador;
}


void pulsadorAlarma(){
  int lecturaPulsador = digitalRead(BUTTON_ALARMA_PIN);
  //Si se cambio el estado ya sea por un ruido o por haber pulsado
  if (lecturaPulsador != ultValorBotonAlarma) {
    // reseteo el tiempo de delay
    ultLecturaBotonAlarma = millis();
  }

  if ((millis() - ultLecturaBotonAlarma) > DELAY_BOTON) {
    // en este punto se supone que el boton fue presionado 
    // por mas tiempo que el delay

    // hago chequeo del cambio de estado
    if (lecturaPulsador != botonAlarmaEstado) {
      botonAlarmaEstado = lecturaPulsador;

      // si la lectura es LOW quiere decir que se presiono
      if (botonAlarmaEstado == LOW ) {
        estadoAlarma = false;
        digitalWrite(BUZZER_PIN,HIGH);
      }
    }
  }

  // salvo la lectura para la proxima vuelta
  ultValorBotonAlarma = lecturaPulsador;
}

//Funcion analisis de lectura de sensor de movimiento
void sensorPIR(){
  uint8_t lecturaPIR = digitalRead(PIR_PIN);
  unsigned long currentMillis = millis();
  
  if(lecturaPIR == HIGH){
    if(currentMillis - previusMillisPIR > PAUSA_PIR){
        previusMillisPIR = currentMillis;
        estadoLuz = true;
        digitalWrite(LED_PIN,HIGH);
      }   
  }
}

void sensorLDR(bool estadoLectura) {
  int lecturaLDR = digitalRead(LDR_PIN);
  int estadoLecturaInt = estadoLectura?1:0;

  if (lecturaLDR == estadoLecturaInt and bandTimerLDR == false){
    bandTimerLDR = true;
      previousMilisLDR = millis();
  }
  else {
      if (lecturaLDR == estadoLecturaInt and bandTimerLDR == true) {
        if(millis() - previousMilisLDR > TIEMPO_ESPERA_LDR){
            
            
            
            if(estadoLectura == !posicionPersiana){
              persiana(estadoLectura);
              posicionPersiana = !posicionPersiana;
            }
            cambioEstadoLuz(estadoLectura);
            estadoLuzLDR = !estadoLuzLDR;
            estadoLuz = !estadoLuz;
            bandTimerLDR = false;
        }
      } 
      else {
        bandTimerLDR = false;
      }
  }
}

void clapOn(){
  int valorSonido = analogRead(MICROFONO_PIN);
  tiempoAplausoAct = millis();

  if (valorSonido > APLAUSO) { // si se detecta un aplauso
    if (
      (tiempoAplausoAct > tiempoUltAplauso + DESV_MISMO_APL) && // para que no tome el mismo aplauso como un segundo aplauso
      (ultValorSonido < APLAUSO) &&  // si es el primer aplauso
      (tiempoAplausoAct < tiempoUltAplauso + BRECHA_SEG_APLAU) && // brecha de 0.5 segundos para el segundo aplauso
      (tiempoAplausoAct > ultCbioLuz + DESVIO_3_APLAUSOS)) { // desvio para que no haya un tercer aplauso
    
      estadoLuz = !estadoLuz;
      
      cambioEstadoLuz(estadoLuz);

      ultCbioLuz = tiempoAplausoAct;
    }

    tiempoUltAplauso = tiempoAplausoAct;
  }
  ultValorSonido = valorSonido;
}

void buzz(){
  unsigned long currentMillis = millis();
 
  if(currentMillis - previousMilisAlarma > INTERVALO_ALARMA) {
    previousMilisAlarma = currentMillis;   
 
    if (estadoBuzzer == LOW)
      estadoBuzzer = HIGH;
    else
      estadoBuzzer = LOW;
 
    digitalWrite(BUZZER_PIN, estadoBuzzer);
  }
}

//Funcion para verificar si es la hora seteada como alarma.
void checkAlarmas(){
  DateTime ahora = RTC.now();
  int hora = ahora.hour();
  int minuto = ahora.minute();
  for(int i=0 ; i < 5 ; i++){
    if(arrayAlarmasHora[i] == hora && arrayAlarmasMinutos[i] == minuto){
      estadoAlarma = true;
      deleteAlarma(i);
      break;
    }
  }
}
//Funcion para eliminar alarmas por posición.
void deleteAlarma(int i){
   arrayAlarmasHora[i] = 99;
   arrayAlarmasMinutos[i] = 99;
}

//Función para eliminar alarmas por hora/minuto.
void deleteAlarma(int hora, int minuto){
  for(int i=0 ; i < 5 ; i++){
      if(arrayAlarmasHora[i] == hora && arrayAlarmasMinutos[i] == minuto){
        arrayAlarmasHora[i] = 99;
        arrayAlarmasMinutos[i] = 99;
        break;
    }
  }
}
//Función para agregar alarma.
void addAlarma(int hora, int minuto){
  for(int i=0 ; i < 5 ; i++){
    if(arrayAlarmasHora[i] == 99){
      arrayAlarmasHora[i] = hora;
      arrayAlarmasMinutos[i] = minuto;
      break;
    }
  }
}
//Función para enviar datos a la aplicación por bluetooth.
void enviarInfoBluetooth(){

  unsigned long currentMillis = millis();
 
  if(currentMillis - previousMilisSendBT > INTERVALO_SEND_BT) {
    previousMilisSendBT = currentMillis; 
    DateTime hora = RTC.now();
        
    Serial.print(digitalRead(PIR_PIN));
    Serial.print('|');
    Serial.print(digitalRead(LDR_PIN));
    Serial.print('|');
    Serial.print(posicionPersiana);
    Serial.print('|');
    Serial.print(estadoLuz);
    Serial.print('|');
    Serial.print(hora.hour(), DEC);
    Serial.print(':');
    Serial.print(hora.minute(), DEC);
    Serial.print('#');
  }
  
}
//Función para recibir datos de la aplicación por bluetooth.
void recibirBluetooth(String dato, bool persianaOn){
  //SHAKE Y BOTON LUZ - Encender/apagar luz
  if(dato[0] == '0'){
        estadoLuz = !estadoLuz;
        cambioEstadoLuz(estadoLuz);  
    }
   //BOTON SENSOR LDR - Desactivo/Activo Sensor LDR
   if(dato[0] == '1'){
      previousMilisLDR = millis();
      ldrHabilitado = !ldrHabilitado;
   }
   //SENSOR HUELLA - Subir/Bajar persiana
   if(dato[0] == '2' and !persianaOn){
      persiana(!posicionPersiana);
      posicionPersiana = !posicionPersiana;
   }
   //BOTON ALARMA - Agregar alarma
   if(dato[0] == '3'){
      int hora = dato.substring(2,4).toInt();
      int minuto = dato.substring(4,6).toInt();
      addAlarma(hora,minuto);
    }
   //BOTON ALARMA - Eliminar alarma
    if(dato[0] == '4'){
      int hora = dato.substring(2,4).toInt();
      int minuto = dato.substring(4,6).toInt();
      deleteAlarma(hora,minuto);
    }
    //SENSOR PROXIMIDAD - Activar/Desactivar sensor PIR
    if(dato[0] == '5'){
      pirHabilitado = !pirHabilitado;
    }
    //Programar la Hora y Fecha
    if(dato[0] == '6'){
      int anio = dato.substring(2,4).toInt();
      int mes= dato.substring(10,2).toInt();
      int dia = dato.substring(7,2).toInt();
      int hora= dato.substring(13,2).toInt();
      int minuto= dato.substring(16,2).toInt();
      int seg= dato.substring(19,2).toInt();
      RTC.adjust(DateTime(anio,mes,dia,hora,minuto,seg)); 
    }
  }

  void cambioEstadoLuz(bool estadoLuz){
    if(estadoLuz){
        digitalWrite(LED_PIN,HIGH);        
      }
    else{
        digitalWrite(LED_PIN,LOW);
        previusMillisPIR = millis();
    }  
  }

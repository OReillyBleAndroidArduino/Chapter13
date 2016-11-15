#include "CurieBle.h"

static const char* bluetoothDeviceName = "EchoServer";

static const int   characteristicTransmissionLength = 20;

char bleMessage[characteristicTransmissionLength];
int bleMessageLength;
const char* uuid;
bool bleDataWritten = false; 


BLEService service("180C");
BLECharacteristic characteristic(
  "2A56",
  BLEWrite | BLERead | BLENotify,
  characteristicTransmissionLength
);

BLEPeripheral blePeripheral;


void onCharacteristicWritten(BLECentral& central, BLECharacteristic &characteristic) {
  bleDataWritten = true;
  uuid = characteristic.uuid();

  bleMessageLength = characteristic.valueLength();
  strcpy((char*) bleMessage, (const char*) characteristic.value());
}

// Central connected.  Print MAC address
void onCentralConnected(BLECentral& central) {
  Serial.print("Central connected: ");
  Serial.println(central.address());
}

// Central disconnected
void onCentralDisconnected(BLECentral& central) {
  Serial.println("Central disconnected");
}


void sendBleMessage(unsigned char* bleMessage) {
  Serial.print("Sending message: ");
  Serial.println((char*) bleMessage);
  characteristic.setValue((const unsigned char*) bleMessage, bleMessageLength);
}

void setup() {
  Serial.begin(9600);
  while (!Serial) {;}
  blePeripheral.setLocalName(bluetoothDeviceName);

  // attach callback when central connects
  blePeripheral.setEventHandler(
    BLEConnected,
    onCentralConnected
  );
  // attach callback when central disconnects
  blePeripheral.setEventHandler(
    BLEDisconnected,
    onCentralDisconnected
  );
  
  blePeripheral.setAdvertisedServiceUuid(service.uuid());
  blePeripheral.addAttribute(service);
  blePeripheral.addAttribute(characteristic);

  characteristic.setEventHandler(
    BLEWritten,
    onCharacteristicWritten
  );

  Serial.print("Starting ");
  Serial.println(bluetoothDeviceName);
  blePeripheral.begin();
}



void loop() {
  if (bleDataWritten) {
    bleDataWritten = false; // ensures only happens once

    Serial.print("Incoming message found: ");
    Serial.println((char*) bleMessage);
    sendBleMessage((unsigned char*)bleMessage);
  }

}

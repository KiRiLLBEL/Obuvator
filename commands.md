# Команды для общения с платами

## Возможный функционал

1. Получить значение напряжения батареи - ``VBAT``
```
AT+VBAT\r\n
OK+VBAT=<UINT(0, 4095)>\r\n
```
2. Начать вращение мотора по часовой - ``RCW``
```
AT+RCW\r\n
OK+RCW\r\n
```
3. Начать вращение мотора против часовой - ``RCCW``
```
AT+RCCW\r\n
OK+RCCW\r\n
```
4. Остановить мотор - ``STOP``
```
AT+STOP\r\n
OK+STOP\r\n
```
5. Перевести кроссы в спящий режим - ``SLEEP``
```
AT+SLEEP\r\n
OK+SLEEP\r\n
```
6. Получить значение энкодера - ``ROT``
```
AT+ROT\r\n
OK+ROT=<UINT(0, 65535)>\r\n
```
7. Получить значение термометра - ``TEMP``
```
AT+TEMP\r\n
OK+TEMP=<UINT(Celsius)>\r\n
```
8. Задать время перехода в спящий режим - ``IDLETIME``
```
AT+IDLETIME=<UINT(miliseconds)>\r\n
OK+IDLETIME\r\n
```

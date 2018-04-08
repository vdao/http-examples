# 1. Сертификаты

1.1 терминология 

Стандарт X.500 определяет следующие атрибуты, используемые для идентификации владельца сертификата

CN=commonName   Имя субъекта, например "Сара Конор"
OU=organizationUnit Название подразделения организации. 
O=organizationName Название организации  
L=localityName  Название места расположения организации (город, район)
S=stateName Название штата
C=country   Код странц (2 буквы: "US", "CH")


DER - бинарный формат

PEM - ключ в формате base64 согласно спецификации [Internet RFC 1421 Certificate Encoding Standard.]()
Ключ в формате PEM выглядит так:

```
-----BEGIN CERTIFICATE-----

encoded certificate goes here. 

-----END CERTIFICATE-----
``` 


С помощью инструмента [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html) герерируем сертификат с алиасом 'my_cert' и сохраняем его в ключницу keystore.jks с паролем 'qwerty'

```bash
> keytool -genkey -keyalg RSA -alias my_cert -dname CN=user@example.com -keystore keystore.jks -storepass qwerty -validity 360 -keysize 2048
```

Настраиваем https в application.yml [Spring Boot: SSL How To](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-configure-ssl)

```yaml
server:
  port: 8443
  ssl:
    key-store: classpath:keystore.jks
    key-store-password: qwerty
    key-password: qwerty
    key-alias: my_cert
```

Запускаем сервер и пытаемся выполнить запрос с помощью curl. 
Поскольку сервер использует самоподписанный сертификат, curl выдаст ошибку.  

```text
$ curl "https://localhost:8443/ping"
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
curl: (60) SSL certificate problem: self signed certificate
More details here: https://curl.haxx.se/docs/sslcerts.html

curl performs SSL certificate verification by default, using a "bundle"
 of Certificate Authority (CA) public keys (CA certs). If the default
 bundle file isn't adequate, you can specify an alternate file
 using the --cacert option.
If this HTTPS server uses a certificate signed by a CA represented in
 the bundle, the certificate verification probably failed due to a
 problem with the certificate (it might be expired, or the name might
 not match the domain name in the URL).
If you'd like to turn off curl's verification of the certificate, use
 the -k (or --insecure) option.

```

Проверку сертификата можно отключить опцией -k
 
```text
$ curl -ks "https://localhost:8443/ping"
pong
```

Добавляем требование клиентской аутентификации с помощью сертификатов в application.yml
```yaml
# Whether client authentication is wanted ("want") or needed ("need"). Requires a trust store.
server.ssl.client-auth=need
```

Теперь при попытке обратиться к серверу без указания клиентского сертификата клиент получает ошибку: 

```text
$ curl -k "https://localhost:8443/ping"
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
curl: (35) error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate
```

Сгенерируем дополнительный сертификат для клиентской авторизации при помощи утилиты openssl.


.pem, .crt, .cer — готовый, подписанный центром сертификации сертификат, расширения разные, но означают одно и то же. Если совсем просто, то сертификат, это подписанный открытый ключ, плюс немного информации о вашей компании;
.key — закрытый или открытый ключ;
.csr — запрос на подпись сертификата, в этом файле хранится ваш открытый ключ плюс информация, о компании и домене, которую вы указали.

Создаем закрытый ключ и запрос на выпуск сертификата 

    $ openssl req -newkey rsa:2048 -nodes -keyout domain.key -out domain.csr
    
Создадим самоподписанный x509 сертификат

    $ openssl x509 -signkey domain.key -in domain.csr -req -days 365 -out domain.crt
    
    
В результате должны получить три файла:

    $ ls
    domain.crt  domain.csr  domain.key
  
Просмотр сертификатов в формате pem

    $ openssl req -text -noout -verify -in domain.csr
    
```text
$ openssl req -text -noout -verify -in domain.csr
verify OK
Certificate Request:
    Data:
        Version: 0 (0x0)
        Subject: C=AU, ST=Some-State, O=Internet Widgits Pty Ltd, CN=user@mycompany.com
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (2048 bit)
```

Протестируем сертификат для подключения к нашему сервису.


```text
$ curl -k 'https://localhost:8443/ping' -v --key ./domain.key --cert ./domain.crt
* Closing connection 0
curl: (35) error:14094416:SSL routines:ssl3_read_bytes:sslv3 alert certificate unknown
```

Исправим эту ошибку, добавив сертификат в трастстор. Для этого сначала сконвертируем сертификат в бинарный формат der:

```text
openssl x509 -outform der -in certificate.pem -out certificate.der
```

И затем импортируем сертификат в ключницу:

```text
$ keytool -import -alias client-key -keystore truststore.jks -file domain.der
Enter keystore password:  qwerty
Re-enter new password: qwerty
Owner: CN=user@mycompany.com, O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
Issuer: CN=user@mycompany.com, O=Internet Widgits Pty Ltd, ST=Some-State, C=AU
Serial number: 9dd798f4cdcf4a27
Valid from: Sun Apr 08 17:53:26 MSK 2018 until: Mon Apr 08 17:53:26 MSK 2019
Certificate fingerprints:
         MD5:  17:F7:8A:C1:5A:23:B2:64:E2:CB:58:E6:DE:DE:D3:8B
         SHA1: 2C:7B:ED:7A:0E:4D:6F:0E:B5:90:4A:71:E5:D1:70:3D:46:65:87:E2
         SHA256: CE:60:7F:7E:79:E1:84:5A:F9:3A:7B:92:5D:B6:8E:FC:49:45:7C:C6:02:A8:9A:98:26:AD:F2:AF:AE:A1:71:EE
         Signature algorithm name: SHA256withRSA
         Version: 1
Trust this certificate? [no]:  yes
Certificate was added to keystore
```

Снова пробуем выполнить запрос при помощи curl, получаем ответ:

```text
$ curl -sk 'https://localhost:8443/ping' --key ./domain.key --cert ./domain.crt
pong
```

Теперь попробуем выполнить тот же запрос из java-клиента. На время отладки включим опцию:
```text
-Djavax.net.debug=all
```
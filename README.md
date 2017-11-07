> Consider your origins: you were not made to live as brutes, but to follow virtue and knowledge.

> \- Dante Alighieri

As it stands, I upload screenshots to my own private server as an alternative to other services.
- puu.sh
- sharex
- imgur
- etc

Dante provides a way to upload to your own server, create accounts, see upload history, etc.

I use the following script to upload images.
Planning on writing a Clojure screenshot tool soon.

```
#!/bin/sh
tmp=$(mktemp /tmp/XXXXXXXXXXXXXXXXXXX.png)
xclip -i -selection clipboard -t text/uri-list $tmp

sleep 0.2;
gnome-screenshot -a -f $tmp

tmpsize=$(wc -c <"$tmp")
if [ $tmpsize != 0 ]; then
        out=$(curl -X POST -H "content-type: multipart/form-data" YOURURL.WTF/upload -F "key=YOURKEY" -F "image=@$tmp")
        final=$(sed -e 's/^"//' -e 's/"$//' <<<"$out")
        echo $final | xclip -selection clipboard
        xdg-open $final
fi
```


You need to generate your own pub/privkeys to use.

```
openssl genrsa -aes128 -out auth_privkey.pem 2048
openssl rsa -pubout -in auth_privkey.pem -out auth_pubkey.pem
```

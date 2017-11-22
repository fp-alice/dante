
#!/bin/sh
tmp=$(mktemp /tmp/XXXXXXXXXXXXXXXXXXX.png)
xclip -i -selection clipboard -t text/uri-list $tmp
sleep 0.2;
gnome-screenshot -a -f $tmp
tmpsize=$(wc -c <"$tmp")
if [ $tmpsize != 0 ]; then
    auth="USER_KEY"
    out=$(curl -X POST -H "content-type: multipart/form-data" SITE_URL -F "key=${auth}" -F "image=@$tmp")
    final=$(echo $out | awk -F"\":\"" '{print $2}' | cut -f1 -d"\"")
    echo $final | xclip -selection clipboard
    xdg-open $final
fi

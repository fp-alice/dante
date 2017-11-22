#!/bin/sh
tmp=$(mktemp /tmp/XXXXXXXXXXXXXXXXXXX.png)
xclip -i -selection clipboard -t text/uri-list $tmp
sleep 0.2;
gnome-screenshot -a -f $tmp
tmpsize=$(wc -c <"$tmp")
if [ $tmpsize != 0 ]; then
    auth="59fa9f5f5cd7dd3d64c889a2"
    echo "key=${auth}"
    out=$(curl -X POST -H "content-type: multipart/form-data" http://localhost:3000/api/upload -F "key=${auth}" -F "image=@$tmp")
    final=$(echo $out | awk -F"\":\"" '{print $2}' | cut -f1 -d"\"")
    echo $final | xclip -selection clipboard
    echo $final
    xdg-open $final
fi

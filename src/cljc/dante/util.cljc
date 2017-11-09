(ns dante.util)

(defn foo-cljc [x]
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn format-str [strings & {:keys [init]
                             :or   {init ""}}]
  "Prepare `strings` to print, optionally prepend with `init`"
  (reduce str init (vec (interpose " # " (remove empty? (map str strings))))))

(defn frame-text [& strings]
  "Frrame and print `strings`"
  (let [text (format-str strings)
        len  (count text)
        left (- 81 len)
        each (/ left 2)
        spacer (reduce str (repeat each "─"))
        res (str "├" (reduce str (rest spacer)) "┤ " text " ├" spacer)
        res (if (= 0 (mod len 2)) (str res "─") res)]
    (println res)))

(defn info [& strings]
  (println (format-str strings :init "├╼ ")))

(defn in?
  "Checks if seq `coll` contains member `elem`."
  [coll elem]
  (let [coll    (seq coll)
        non-nil (not-any? nil? (vector coll elem))]
    (if non-nil
      (some #(= elem %) coll)
      false)))

(defn url [prod?]
  (if prod?
    "http://shekels.wtf/"
    "http://localhost:3000/"))

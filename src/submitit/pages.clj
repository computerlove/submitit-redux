(ns submitit.pages 
  (:use 
    [submitit.base]    
    [submitit.cj]
    [submitit.core]
    [submitit.email]
    [noir.core]
    [noir.request]
    [noir.response :only [redirect]]
    [cheshire.core :only [generate-string parse-string]]
    [hiccup.page-helpers :only [html5]]
  )
  (:require [noir.server :as server])

)

(def handler (server/gen-handler {:mode :dev
                                  :ns 'submitit.core}))


(defn startup []  
  (let [mode :dev
        port (Integer. (get (System/getenv) "PORT" "8080"))
        ]
    (server/start port {:mode mode
                        :ns 'submitit.core}))
)

(defn -main [& m]
  (println "Starting " (java.lang.System/getenv "SUBMITIT_SETUP_FILE"))
;  (println (read-setup :serverhostname))
  ;(java.lang.System/set) "SUBMITIT_SETUP_FILE" nil)]
  ;(dosync (ref-set setupenv (read-enviroment-variables (first m))))
  (startup)
    )
            
(defpage [:get "/tagCollection"] {:as nothing}
  (generate-string (tag-list))
  )

(defpage [:get "/newSpeakerId"] {:as nothing}
  (let [nid (dosync (let [res @speaker-dummy-id] 
    (ref-set speaker-dummy-id (inc @speaker-dummy-id))
    res))
  ]
  (generate-string {:dummyId (str "DSI" nid)})
  )
  )


(defpage [:get "/"] {:as attrs}
  (redirect (if (attrs :talkid) (str "index.html?talkid=" (attrs :talkid)) "index.html"))
  ) 


(defpartial page-header[] 
  [:head 
  [:link {:href "css/bootstrap.min.css" :rel "stylesheet"}]
  [:script {:src "js/jquery-1.7.2.js"}]
  [:script {:src "js/bootstrap.min.js"}]
    ]
  )

(defpage [:post "/addTalk"] {:as empty-post}
  (let [talk (parse-string (slurp ((noir.request/ring-request) :body)))]
    (println "+++TALK+++" talk "+++")    
    (if (captcha-error? (talk "captchaAnswer") (talk "captchaFact")) 
        (let [errme (generate-string {:captchaError true})]
          (println "CaptchError:" + errme)
          errme
          )
      (let [error-response (validate-input talk)]
        (if error-response error-response
          (let [talk-result (communicate-talk-to-ems talk)]
            (println "TALKRES:" talk-result)
            (send-mail (speaker-mail-list talk) (str "Confirmation " (if (talk "add ") "on updating" "of") " your JavaZone 2013 submission \"" (talk "title") "\"") (generate-mail-text (slurp (clojure.java.io/resource "speakerMailTemplate.txt")) 
              (assoc talk "talkmess" (generate-mail-talk-mess talk-result))))    
            (generate-string (merge talk-result 
              (if (talk-result :submitError) {:retError true :addr "xxx"} {:retError false :addr (str (read-setup :serverhostname) "/talkDetail?talkid=" (talk-result :resultid))})))
          )
        )
      )
  )))


(defpage [:get "/talkDetail"] {:as attrs}
  (redirect (if (attrs :talkid) (str "talkDetail.html?talkid=" (attrs :talkid)) "index.html"))  
  )

(defpage [:get "/savedpic"] {:as param}
  (noir.response/content-type "image/jpeg"
  (new java.io.FileInputStream (new java.io.File (decode-string (param :picid)))))
)

(defpage [:get "/speakerPhoto"] {:as param}    
    (let [connection (.openConnection (new java.net.URL (decode-string (param :photoid))))]
      (.setRequestMethod connection "GET")      
      (.connect connection)
      (noir.response/content-type (.getContentType connection)
      (.getInputStream connection))
    )
)


(defpage [:get "/status"] {:as nothing}
  (let [setupfile (get-setup-filename)]
  (html5
    [:body
      [:h1 "Status"]
      [:p (str "EnvFile: '" setupfile "'")]
      [:hr]
      (if (and setupfile (.exists (new java.io.File setupfile)))
      [:pre (setup-str (slurp setupfile)  )]
      [:p "Could not find setupfile"])
      [:hr]
      [:pre (reduce (fn[a b] (str a "\n" b)) (java.lang.System/getProperties))]
    ]
    )
  ))


(defpage [:get "/needPassword"] {:as nothing}
  (generate-string {:needPassword (need-submit-password?)})
  )


(defpage [:get "/talkJson"] {:as talkd}
  (if (frontend-develop-mode?) (slurp (clojure.java.io/resource "exampleTalk.json"))
  (let [decoded-url (decode-string (talkd :talkid))] 
  (let [item (fetch-item decoded-url) speaker-list (speakers-from-item item)]
    (generate-string
      {
        :presentationType (item "format"),
        :title (item "title"),
        :abstract (item "body"),
        :language (item "lang"),
        :level (item "level"),
        :outline (item "outline"),
        :highlight (item "summary"),
        :equipment (item "equipment")
        :expectedAudience (item "audience")
        :talkTags (item "keywords")
        :addKey (talkd :talkid)
        :lastModified (item :lastModified)
        :speakers speaker-list
      }      
  )))))

(defpage [:get "/loadCaptcha"] {:as noting}
  (let [gen-cap (build-captcha)]
    (noir.session/put! :capt-image (.getImage gen-cap))
    (generate-string {:fact (noir.util.crypt/encrypt random-salt (.trim (.getAnswer gen-cap)))})
    ) 
)

(defpage [:get "/captcha"] {:as noting}
  (noir.response/content-type 
    "image/jpeg" 
    (let [out (new java.io.ByteArrayOutputStream)]
      (javax.imageio.ImageIO/write (noir.session/get :capt-image) "png" out)      
      (new java.io.ByteArrayInputStream (.toByteArray out))))  
  )

(defn upload-form [message speaker-key dummy-key]
  (html5 
    [:body
    (if message [:p message])
    [:form {:method "POST" :action "addPic" :enctype "multipart/form-data"}
      [:input {:type "file" :name "filehandler" :id "filehandler" :required "required"}]
      [:input {:type "hidden" :value speaker-key :name "speakerKey" :id "speakerKey"}]
      [:input {:type "hidden" :value dummy-key :name "dummyKey" :id "dummyKey"}]
      [:input {:type "submit" :value "Upload File"}]    
    ]]
  )

)

(defpage [:get "/uploadPicture"] {:as paras}
  (upload-form nil (paras :speakerid) (paras :dummyKey))
  )

(defpage [:post "/addPic"] {:keys [filehandler speakerKey dummyKey]}
  (println "***")
  (println filehandler)
  (println speakerKey)
  (println dummyKey)
  (println "***")
;  (println (type (filehandler :tempfile)))
;  (println "***")
;  (another-add-photo (str (decode-string speakerKey) "/photo") (to-byte-array (photo-map :tempfile)) filehandler)

  (let [photo-byte-arr (to-byte-array (filehandler :tempfile)) photo-content-type (filehandler :content-type) photo-filename (filehandler :filename)]
    (cond 
      (> (count photo-byte-arr) 500000) (upload-form "Picture too large (max 500k)" speakerKey dummyKey)
      (not= "XX" dummyKey) (do 
          (noir.session/put! dummyKey {:photo-byte-arr photo-byte-arr :photo-content-type photo-content-type :photo-filename photo-filename})
          (upload-form (str "Picture uploaded: " (filehandler :filename)) speakerKey dummyKey)
        )
      :else (do 
        (add-photo (str (decode-string speakerKey) "/photo") photo-byte-arr photo-content-type photo-filename)        
        (upload-form (str "Picture uploaded: " (filehandler :filename)) speakerKey dummyKey))
    )

  
  )
)
(ns ledger-observer.socket-test
  (:require [cljs.test :refer-macros [deftest is]]
            [ledger-observer.socket :as socket]
            [ledger-observer.visualization.data :as data]
            [cljs.core.async :refer [<! >! go-loop chan alt! go]]
             ))

(def example-json "{\"engine_result\":\"tecPATH_DRY\",\"engine_result_code\":128,\"engine_result_message\":\"Path could not send partial amount.\",\"ledger_hash\":\"4170BD9BF60D19472491C383FD164CFAD2A70445420958909550673AFFE196D4\",\"ledger_index\":42319698,\"meta\":{\"AffectedNodes\":[{\"ModifiedNode\":{\"FinalFields\":{\"Account\":\"re3coCubt6kkj9F7k1kVrwooLGFD1i1aU\",\"Balance\":\"2285404890\",\"Flags\":0,\"OwnerCount\":2,\"Sequence\":493975},\"LedgerEntryType\":\"AccountRoot\",\"LedgerIndex\":\"E15F831363B294FFEA8D77C9689D96D1537E1B2505310AE1D5516BD70025B9C2\",\"PreviousFields\":{\"Balance\":\"2285404920\",\"Sequence\":493974},\"PreviousTxnID\":\"77B301B1706B73B4ACDF7B3C92C9BB2BD683F510FEE807C26718F2E2B892BAC3\",\"PreviousTxnLgrSeq\":42319698}}],\"TransactionIndex\":8,\"TransactionResult\":\"tecPATH_DRY\"},\"status\":\"closed\",\"transaction\":{\"Account\":\"re3coCubt6kkj9F7k1kVrwooLGFD1i1aU\",\"Amount\":{\"currency\":\"CNY\",\"issuer\":\"rBd93FBpZgLtn7u74neADjSe9dJUimUw33\",\"value\":\"2255.40501\"},\"Destination\":\"rBd93FBpZgLtn7u74neADjSe9dJUimUw33\",\"Fee\":\"30\",\"Flags\":2147942400,\"LastLedgerSequence\":42319706,\"Paths\":[[{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"razqQKzJRdB4UxFPWf5NEpEG3WMkmwgcXA\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}],[{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"USD\",\"issuer\":\"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}],[{\"currency\":\"XLM\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}],[{\"currency\":\"CNY\",\"issuer\":\"rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XLM\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}],[{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"razqQKzJRdB4UxFPWf5NEpEG3WMkmwgcXA\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}],[{\"currency\":\"CNY\",\"issuer\":\"rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"CNY\",\"issuer\":\"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"USD\",\"issuer\":\"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\",\"type\":48,\"type_hex\":\"0000000000000030\"},{\"currency\":\"XRP\",\"type\":16,\"type_hex\":\"0000000000000010\"}]],\"SendMax\":\"2255405010\",\"Sequence\":493974,\"SigningPubKey\":\"02A0723A03C4BF690C37B6CE0117F1F24426972FFCB0950279DF231E61F1CB5515\",\"TransactionType\":\"Payment\",\"TxnSignature\":\"30440220070268D7F629E24525EF7E09CBB5C88DA0D465642054AE1700DC2EA768ED2AD902201EBEC9C4B13D8C0C4A8281568CE7A5908962C6869D278C1CC3812C587A133127\",\"date\":593174762,\"hash\":\"9A93E0A25DD079E3F09E2EE42F834D48752652BC18E1B60F69B8FCFF3863C6EA\"},\"type\":\"transaction\",\"validated\":true}")

#_(deftest socket-test
    ;; Read about test setup on hasslett
  (let [ripple-socket (chan)
        continuation-channel (chan)
        filter-in (chan)
        filter-out (chan)]
    (socket/kickoff-ripple-socket ripple-socket continuation-channel filter-in filter-out)
    (async done
     (go
       (>! ripple-socket example-json)
       (is (= 1 (<! continuation-channel)))
       (done)
       ))))

(deftest address?
  (let [valid-address "re3coCubt6kkj9F7k1kVrwooLGFD1i1aU"
        invalid-address "asdsasdasd"
        invalid-address2 "e3coCubt6kkj9F7k1kVrwooLGFD1i1aU"]
    (is (socket/address? valid-address))
    (is (not (socket/address? invalid-address)))
    (is (not (socket/address? invalid-address2)))))

(deftest parse-accounts
  (let [parsed (socket/parse-accounts example-json)]
    (is (= "re3coCubt6kkj9F7k1kVrwooLGFD1i1aU" (data/from-new-transaction-event parsed)))
    (is (= ["rBd93FBpZgLtn7u74neADjSe9dJUimUw33"
	          "rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y"
	          "razqQKzJRdB4UxFPWf5NEpEG3WMkmwgcXA"
	          "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
	          "rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7"] (data/targets-new-transaction-event parsed)))
    (is (= "9A93E0A25DD079E3F09E2EE42F834D48752652BC18E1B60F69B8FCFF3863C6EA"
           (data/tid-new-transaction-event parsed)))
    (is (= "Payment" (data/type-new-transaction-event parsed)))))

(deftest contains-address?
  (let [tx-event (data/make-new-transaction-event
                  "9A93E0A25DD079E3F09E2EE42F834D48752652BC18E1B60F69B8FCFF3863C6EA"
                  "re3coCubt6kkj9F7k1kVrwooLGFD1i1aU"
                  ["rBd93FBpZgLtn7u74neADjSe9dJUimUw33"
	                 "rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y"
	                 "razqQKzJRdB4UxFPWf5NEpEG3WMkmwgcXA"
	                 "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
	                 "rPT74sUcTBTQhkHVD54WGncoqXEAMYbmH7"]
                  "Payment")]
        (is (socket/contains-address? tx-event ["rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y"]))
        (is (socket/contains-address? tx-event ["asdasd" "rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y"]))
        (is (socket/contains-address? tx-event ["re3coCubt6kkj9F7k1kVrwooLGFD1i1aU"]))
        (is (socket/contains-address? tx-event ["asdasd" "re3coCubt6kkj9F7k1kVrwooLGFD1i1aU"]))
        (is (not (socket/contains-address? tx-event ["asdasd"])))))

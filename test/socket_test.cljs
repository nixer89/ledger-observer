(ns ledger-observer.socket-test
  (:require [cljs.test :refer-macros [deftest is]]
            [ledger-observer.socket :as socket]
            [ledger-observer.visualization.data :as data]
            [cljs.core.async :refer [<! >! go-loop chan alt! go]]
             ))

(def example-json "{
  \"result\": \"success\",
  \"transaction\": {
    \"hash\": \"79B9DB93946565AC9490BA557F5ADD63C3BB08F33587DCD0C293B05A32FB19B7\",
    \"ledger_index\": 49855518,
    \"date\": \"2019-09-06T17:27:32+00:00\",
    \"tx\": {
      \"TransactionType\": \"Payment\",
      \"Flags\": 2147942400,
      \"Sequence\": 407363,
      \"LastLedgerSequence\": 49855518,
      \"Amount\": {
        \"value\": \"50\",
        \"currency\": \"CNY\",
        \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
      },
      \"Fee\": \"12\",
      \"SendMax\": \"50000000000\",
      \"SigningPubKey\": \"EDEACF3881008679017CFC7F46E929B4AD6381DC5E4EAC11F05765DDF0D7C3E15E\",
      \"TxnSignature\": \"279A45E4F90464AEB4DECFFA9F1CF4B66B0E88F38B5ABF705AA56E8719AB767ED53A7B933EA093FAAE76B6ECDA3FC24ADF8A3F27289609C90190512C07526C09\",
      \"Account\": \"rhKTijFuk81aquubbSwzZZNuG4CsYPPXRP\",
      \"Destination\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\",
      \"Memos\": [
        {
          \"Memo\": {
            \"MemoType\": \"636C69656E74\",
            \"MemoData\": \"726D31\"
          }
        }
      ],
      \"Paths\": [
        [
          {
            \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",
            \"currency\": \"ULT\"
          },
          {
            \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\",
            \"currency\": \"CNY\"
          },
          {
            \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\",
            \"currency\": \"USD\"
          },
          {
            \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\",
            \"currency\": \"BTC\"
          },
          {
            \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\",
            \"currency\": \"EUR\"
          },
          {
            \"currency\": \"XRP\"
          }
        ]
      ]
    },
    \"meta\": {
      \"TransactionIndex\": 30,
      \"DeliveredAmount\": {
        \"value\": \"0.002298273\",
        \"currency\": \"CNY\",
        \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
      },
      \"AffectedNodes\": [
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49847911,
            \"PreviousTxnID\": \"04CB1A9C03806208A8C9CBEE0C52D152219FB51B00AB9ECAE1D42DE635079ADC\",
            \"LedgerIndex\": \"05809D077131C4CC4408603DB02497F03CBEF4FA78CDE668540C773126805885\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-99992999.95050062\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 3276800,
              \"LowNode\": \"0000000000000000\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-99992999.94820235\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"CNY\",
                \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
              },
              \"HighLimit\": {
                \"value\": \"100000000000\",
                \"currency\": \"CNY\",
                \"issuer\": \"rhKTijFuk81aquubbSwzZZNuG4CsYPPXRP\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"AccountRoot\",
            \"PreviousTxnLgrSeq\": 49854479,
            \"PreviousTxnID\": \"9F48EBAA75E72F9AA8A37A3A154A2C260D626885EB99F1E8ECA27E4F81524C54\",
            \"LedgerIndex\": \"084F9DC5D977FBB3AEE0B1CFECF00693B3CA207A3F6E34E8BB71DC1AF0F7C285\",
            \"PreviousFields\": {
              \"OwnerCount\": 20
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 286046,
              \"OwnerCount\": 19,
              \"Balance\": \"326927237\",
              \"Account\": \"rpMPeUbbsTXUar7Z6CJxRuPHvUSiFC1q6B\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49817180,
            \"PreviousTxnID\": \"9644CD355475D6731F5AB24F5634AAB740AC7D94F47EADEBBFD447844EEAECC5\",
            \"LedgerIndex\": \"252D10CCD0AF715D9D06B2968D428AC10E12BFBDF696E5CE160BD746FA4CD182\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-0.0224596248188287\",
                \"currency\": \"BTC\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"0000000000000FE6\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-0.02240505478430343\",
                \"currency\": \"BTC\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              },
              \"HighLimit\": {
                \"value\": \"100000000\",
                \"currency\": \"BTC\",
                \"issuer\": \"rpmL45YbZWKgp8AH8EjBSknWo5c8dNuuBM\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49854467,
            \"PreviousTxnID\": \"19A91260718F1B2CCEE7B4CDC06F0139D8CDE7F3E1969251B6105E5364B2A1B6\",
            \"LedgerIndex\": \"42415EB4C6D30F43420EA7B726B3162C8DAA053379F8F04F27834A50CDAA8E9F\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"183153.1251330073\",
                \"currency\": \"ULT\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 1114112,
              \"LowNode\": \"0000000000000001\",
              \"HighNode\": \"00000000000003F1\",
              \"Balance\": {
                \"value\": \"183170.9388989451\",
                \"currency\": \"ULT\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"100000000\",
                \"currency\": \"ULT\",
                \"issuer\": \"rpMPeUbbsTXUar7Z6CJxRuPHvUSiFC1q6B\"
              },
              \"HighLimit\": {
                \"value\": \"0\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49849735,
            \"PreviousTxnID\": \"2D4FE300B2525CF7A655BFAD7204AC9CCE058199716FAE22451557A2C6ADDD55\",
            \"LedgerIndex\": \"436680584512838F5DC460557C199DE9420D6ABD36DCA547EF8F16C274E11FA7\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-55998.03762034973\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"000000000000000E\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-56002.28620352591\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"HighLimit\": {
                \"value\": \"10000000\",
                \"currency\": \"CNY\",
                \"issuer\": \"r4L6ZLHkTytPqDR81H1ysCr6qGv9oJJAKi\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49854467,
            \"PreviousTxnID\": \"19A91260718F1B2CCEE7B4CDC06F0139D8CDE7F3E1969251B6105E5364B2A1B6\",
            \"LedgerIndex\": \"44C54535F7F1B266B769AE8C3C9E21FD8CB96B36E57A706E063025B32BD2EE4D\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"61114.24858317618\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 1114112,
              \"LowNode\": \"0000000000000000\",
              \"HighNode\": \"0000000000000020\",
              \"Balance\": {
                \"value\": \"61110\",
                \"currency\": \"CNY\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"1000000\",
                \"currency\": \"CNY\",
                \"issuer\": \"rpMPeUbbsTXUar7Z6CJxRuPHvUSiFC1q6B\"
              },
              \"HighLimit\": {
                \"value\": \"0\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49855242,
            \"PreviousTxnID\": \"36F6568C3DFD3536211F901788248BF9D81F80A0204615B30B37F8EE922BFD1D\",
            \"LedgerIndex\": \"67343A637D723C3B86710F0C1D1C2A026D477D9EFF938E4209AAFEEB801A67E5\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-2147.987532567133\",
                \"currency\": \"EUR\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"0000000000001931\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-2148.529649080954\",
                \"currency\": \"EUR\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              },
              \"HighLimit\": {
                \"value\": \"1000000000\",
                \"currency\": \"EUR\",
                \"issuer\": \"rLgPUBGxUPDH4zzZ3wrzpHGe1mHJXydf24\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"DirectoryNode\",
            \"LedgerIndex\": \"727CE7A3DE7CFB45854E6145C3CAC42DEFB3395BE048176D97151A035DBC648C\",
            \"FinalFields\": {
              \"Flags\": 0,
              \"RootIndex\": \"F99CAB34B453498C3BB4DD6C68B6E256F93FFE27DFCE3DEDA8D1C3C9CAC03618\",
              \"Owner\": \"rpMPeUbbsTXUar7Z6CJxRuPHvUSiFC1q6B\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49854467,
            \"PreviousTxnID\": \"19A91260718F1B2CCEE7B4CDC06F0139D8CDE7F3E1969251B6105E5364B2A1B6\",
            \"LedgerIndex\": \"85A32EF421D6945D40576364456F5B1D56261A33138C86FB7349E45C2A731901\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-71101.38582972946\",
                \"currency\": \"ULT\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"00000000000003F1\",
              \"HighNode\": \"0000000000000001\",
              \"Balance\": {
                \"value\": \"-71083.5720637916\",
                \"currency\": \"ULT\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"HighLimit\": {
                \"value\": \"100000000\",
                \"currency\": \"ULT\",
                \"issuer\": \"r4NJh4vjLowox8SXcALfbdRt7aSa2KG21k\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"AccountRoot\",
            \"PreviousTxnLgrSeq\": 49854478,
            \"PreviousTxnID\": \"1D382C73E77AEA692E88E47E186581C5490384EC94E52A81EB923345041120E2\",
            \"LedgerIndex\": \"8DA1FB7647E7DA0C38630F247C6C649D79B75E8E9D0D5F43FAD6B03C30564FB2\",
            \"PreviousFields\": {
              \"Balance\": \"27640460092\"
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 489544,
              \"OwnerCount\": 18,
              \"Balance\": \"27642758068\",
              \"Account\": \"r4NJh4vjLowox8SXcALfbdRt7aSa2KG21k\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49817183,
            \"PreviousTxnID\": \"F60C0069ACF1CD2629B61462B82E29AFDA9C94694D2909AA5E946EC7C47F9C9D\",
            \"LedgerIndex\": \"9D3B3BCAC64E4977C2AD67F957258267D9706CD0BEFDA4CA696599AEDD6EB22A\",
            \"PreviousFields\": {
              \"TakerPays\": {
                \"value\": \"2.3964481\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              },
              \"TakerGets\": {
                \"value\": \"0.00022237252\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 323741,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000001\",
              \"BookDirectory\": \"AF34014D45A4B8992929E8CFBFEF1AC6AC7338640E3D760A5903D4233EBF4212\",
              \"TakerPays\": {
                \"value\": \"1.808361696360602\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              },
              \"TakerGets\": {
                \"value\": \"0.0001678024854747248\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              },
              \"Account\": \"rpmL45YbZWKgp8AH8EjBSknWo5c8dNuuBM\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"AccountRoot\",
            \"PreviousTxnLgrSeq\": 49850481,
            \"PreviousTxnID\": \"B259580DDE667295041D95461C2D09221061E252C74645BF084C1E1B1B00ACCF\",
            \"LedgerIndex\": \"A95C4B9F82C18381AD25154E6DD1DB66BAA56ACA767E0EAB37FA248CCCA09A71\",
            \"PreviousFields\": {
              \"Sequence\": 407363,
              \"Balance\": \"12158352676\"
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 407364,
              \"OwnerCount\": 31,
              \"Balance\": \"12158352961\",
              \"Account\": \"rhKTijFuk81aquubbSwzZZNuG4CsYPPXRP\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49854998,
            \"PreviousTxnID\": \"36708EB355293828791DA5DAAEF27D64EDE2678747FFC5BE29827A6E53A11352\",
            \"LedgerIndex\": \"C19F10A95E7915583981BC2B884FBA5FDEB9E88E9C83007326CD7EE06CBD8EE0\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-0.1996300673704659\",
                \"currency\": \"BTC\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"0000000000000FED\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-0.1996845284827665\",
                \"currency\": \"BTC\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              },
              \"HighLimit\": {
                \"value\": \"1000000000\",
                \"currency\": \"BTC\",
                \"issuer\": \"rLQp2xx22eXye1rJL4gVwPjNFf5vHVjsXb\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49854471,
            \"PreviousTxnID\": \"18C856586BE97CF4F0D65F480642AF60AA930FB56DA2CAC2BB210E4162AC96B1\",
            \"LedgerIndex\": \"C54C25013DE341821A03E77F3B6F4E856F01A7A2B8F2A28F49CD986F949A6CD7\",
            \"PreviousFields\": {
              \"TakerPays\": \"139753642\",
              \"TakerGets\": {
                \"value\": \"1083.361568322989\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 131072,
              \"Sequence\": 489530,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000001\",
              \"BookDirectory\": \"A26AE9E9B4ACF3D05D3CBAE9675E6B51DC6152CE030DE7C75A04953F8796723C\",
              \"TakerPays\": \"137455666\",
              \"TakerGets\": {
                \"value\": \"1065.547802385126\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"Account\": \"r4NJh4vjLowox8SXcALfbdRt7aSa2KG21k\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49849233,
            \"PreviousTxnID\": \"0089F91C3CB4F610B06F95B0FE5E43805FB9D28F0FE514DCDC2796422319EDEF\",
            \"LedgerIndex\": \"C6674FA08E1A975B8328791087CAEC0BCFFF83F15E1C7D2824A96911A6113CED\",
            \"PreviousFields\": {
              \"TakerPays\": {
                \"value\": \"1007.542584863792\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"TakerGets\": {
                \"value\": \"139.7423834762541\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 131072,
              \"Sequence\": 203664,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000001\",
              \"BookDirectory\": \"B7A806DE52025BB22C244FEDDF0BA9F6A3CC04C8430B936D55199D74F0D4A000\",
              \"TakerPays\": {
                \"value\": \"1003.294001687612\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"TakerGets\": {
                \"value\": \"139.1531208998075\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              },
              \"Account\": \"r4L6ZLHkTytPqDR81H1ysCr6qGv9oJJAKi\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49854998,
            \"PreviousTxnID\": \"36708EB355293828791DA5DAAEF27D64EDE2678747FFC5BE29827A6E53A11352\",
            \"LedgerIndex\": \"C77D252AB8134AFDD22759389798CAE6A986D89E36E1B8671A89CFB46C7EA31C\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-29.395223917908\",
                \"currency\": \"EUR\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"00000000000019B4\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-28.85202317105885\",
                \"currency\": \"EUR\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              },
              \"HighLimit\": {
                \"value\": \"1000000000\",
                \"currency\": \"EUR\",
                \"issuer\": \"rLQp2xx22eXye1rJL4gVwPjNFf5vHVjsXb\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"AccountRoot\",
            \"PreviousTxnLgrSeq\": 49855518,
            \"PreviousTxnID\": \"026EB44CD43CB5683555B575AB792A3375FE19843115C8AF851EF8EE0B422AAF\",
            \"LedgerIndex\": \"CBDF826B1A30729E5D6E7310E95C15C9AD187A395EBBB5EBCC3E93DB681E397D\",
            \"PreviousFields\": {
              \"Balance\": \"5888079328\"
            },
            \"FinalFields\": {
              \"Flags\": 1048576,
              \"Sequence\": 241592,
              \"OwnerCount\": 2,
              \"Balance\": \"5885781055\",
              \"Account\": \"rLgPUBGxUPDH4zzZ3wrzpHGe1mHJXydf24\",
              \"RegularKey\": \"rEAxZg2V9138o52ezuR94jTPswdC4sTtjT\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49854998,
            \"PreviousTxnID\": \"36708EB355293828791DA5DAAEF27D64EDE2678747FFC5BE29827A6E53A11352\",
            \"LedgerIndex\": \"CD9A327008EF18F36254124FC0754967846E773E6CA07B025227ABE1DA0B5754\",
            \"PreviousFields\": {
              \"TakerPays\": {
                \"value\": \"0.00091929\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              },
              \"TakerGets\": {
                \"value\": \"9.169093202027\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 91,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000000\",
              \"BookDirectory\": \"8C7111E0E30AA0E3D31C2A0973C2C7B57CCBF55988136BF051038FDB2B57FAF9\",
              \"TakerPays\": {
                \"value\": \"0.0008648288876993261\",
                \"currency\": \"BTC\",
                \"issuer\": \"rchGBxcD1A1C2tdxF6papQYZ8kjRKMYcL\"
              },
              \"TakerGets\": {
                \"value\": \"8.625892455177845\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              },
              \"Account\": \"rLQp2xx22eXye1rJL4gVwPjNFf5vHVjsXb\"
            }
          }
        },
        {
          \"DeletedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"LedgerIndex\": \"D9309D226183E7AE6A2E92375B1286B682311D9C1DE79FE334872B3350159BA0\",
            \"PreviousFields\": {
              \"TakerPays\": {
                \"value\": \"17.81376593786392\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"TakerGets\": {
                \"value\": \"4.248583176180546\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 286030,
              \"PreviousTxnLgrSeq\": 49854472,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000001\",
              \"PreviousTxnID\": \"9598EC8190BFC2392F4676AAD2BA794FC5CB2058DABF5D9715EF7E35BC95D3D6\",
              \"BookDirectory\": \"E033F6E8E0E1F0FFBE3EB0577F316AFEFB18DE35AE32C77B550EE5651D257362\",
              \"TakerPays\": {
                \"value\": \"0\",
                \"currency\": \"ULT\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"TakerGets\": {
                \"value\": \"0\",
                \"currency\": \"CNY\",
                \"issuer\": \"rKiCet8SdvWxPXnAgYarFUXMh1zCPz432Y\"
              },
              \"Account\": \"rpMPeUbbsTXUar7Z6CJxRuPHvUSiFC1q6B\"
            }
          }
        },
        {
          \"DeletedNode\": {
            \"LedgerEntryType\": \"DirectoryNode\",
            \"LedgerIndex\": \"E033F6E8E0E1F0FFBE3EB0577F316AFEFB18DE35AE32C77B550EE5651D257362\",
            \"FinalFields\": {
              \"Flags\": 0,
              \"ExchangeRate\": \"550EE5651D257362\",
              \"RootIndex\": \"E033F6E8E0E1F0FFBE3EB0577F316AFEFB18DE35AE32C77B550EE5651D257362\",
              \"TakerPaysCurrency\": \"000000000000000000000000554C540000000000\",
              \"TakerPaysIssuer\": \"CED6E99370D5C00EF4EBF72567DA99F5661BFB3A\",
              \"TakerGetsCurrency\": \"000000000000000000000000434E590000000000\",
              \"TakerGetsIssuer\": \"CED6E99370D5C00EF4EBF72567DA99F5661BFB3A\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49847911,
            \"PreviousTxnID\": \"04CB1A9C03806208A8C9CBEE0C52D152219FB51B00AB9ECAE1D42DE635079ADC\",
            \"LedgerIndex\": \"EDD86FED17657616432B64ECD1C8D3615AA48E8DBF126562858740544BEC5D29\",
            \"PreviousFields\": {
              \"TakerPays\": \"99993769950434703\",
              \"TakerGets\": {
                \"value\": \"99993769.95050062\",
                \"currency\": \"CNY\",
                \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 63260,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000000\",
              \"BookDirectory\": \"70184527F7738967ABDC0F7153FD6E46E840966191A980655E038D7EA4C68000\",
              \"TakerPays\": \"99993769948136430\",
              \"TakerGets\": {
                \"value\": \"99993769.94820235\",
                \"currency\": \"CNY\",
                \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
              },
              \"Account\": \"rhKTijFuk81aquubbSwzZZNuG4CsYPPXRP\"
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49849735,
            \"PreviousTxnID\": \"2D4FE300B2525CF7A655BFAD7204AC9CCE058199716FAE22451557A2C6ADDD55\",
            \"LedgerIndex\": \"F47A9F11D73846204865302AD1372B82A3D0901F2D187D4177F98325679D53B4\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-2853.483538008844\",
                \"currency\": \"USD\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"000000000000030D\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-2852.894275432398\",
                \"currency\": \"USD\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              },
              \"HighLimit\": {
                \"value\": \"10000000\",
                \"currency\": \"USD\",
                \"issuer\": \"r4L6ZLHkTytPqDR81H1ysCr6qGv9oJJAKi\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"RippleState\",
            \"PreviousTxnLgrSeq\": 49824586,
            \"PreviousTxnID\": \"FF806F0CD4108DCA2D63DC530FD5DBC510D1A882A6AC86F6B81F1D0D6391D5E8\",
            \"LedgerIndex\": \"F6B15ADB99C19BBF8654A5C596195824EFFC8A561AA6D525FCE8F4FE2691B0F8\",
            \"PreviousFields\": {
              \"Balance\": {
                \"value\": \"-237.2673448954728\",
                \"currency\": \"USD\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              }
            },
            \"FinalFields\": {
              \"Flags\": 2228224,
              \"LowNode\": \"0000000000000578\",
              \"HighNode\": \"0000000000000000\",
              \"Balance\": {
                \"value\": \"-237.8554312991121\",
                \"currency\": \"USD\",
                \"issuer\": \"rrrrrrrrrrrrrrrrrrrrBZbvji\"
              },
              \"LowLimit\": {
                \"value\": \"0\",
                \"currency\": \"USD\",
                \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\"
              },
              \"HighLimit\": {
                \"value\": \"100000000\",
                \"currency\": \"USD\",
                \"issuer\": \"rpmL45YbZWKgp8AH8EjBSknWo5c8dNuuBM\"
              }
            }
          }
        },
        {
          \"ModifiedNode\": {
            \"LedgerEntryType\": \"Offer\",
            \"PreviousTxnLgrSeq\": 49855518,
            \"PreviousTxnID\": \"026EB44CD43CB5683555B575AB792A3375FE19843115C8AF851EF8EE0B422AAF\",
            \"LedgerIndex\": \"FFD4D7D83B7C86056E0CB9434986D186D9FD5811CD3955DF5CE797CEA220D695\",
            \"PreviousFields\": {
              \"TakerPays\": {
                \"value\": \"943.52\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              },
              \"TakerGets\": \"4000000000\"
            },
            \"FinalFields\": {
              \"Flags\": 0,
              \"Sequence\": 241591,
              \"BookNode\": \"0000000000000000\",
              \"OwnerNode\": \"0000000000000000\",
              \"BookDirectory\": \"BC05A0B94DB6C7C0B2D9E04573F0463DC15DB8033ABA85624E086150EC18A000\",
              \"TakerPays\": {
                \"value\": \"942.9778834861785\",
                \"currency\": \"EUR\",
                \"issuer\": \"rhub8VRN55s94qWKDv6jmDy1pUykJzF3wq\"
              },
              \"TakerGets\": \"3997701727\",
              \"Account\": \"rLgPUBGxUPDH4zzZ3wrzpHGe1mHJXydf24\"
            }
          }
        }
      ],
      \"TransactionResult\": \"tesSUCCESS\",
      \"delivered_amount\": {
        \"value\": \"0.002298273\",
        \"currency\": \"CNY\",
        \"issuer\": \"rsdMbYxHmYswHCg1V6vBsnxmHuCjpn6SC4\"
      }
    }
  }
}")

(deftest parse-accounts
  (let [parsed (socket/read-json example-json)
        btx (get parsed "transaction")
        meta (get btx "meta")
        tx (get btx "tx")
        sender (get tx "Account")
        receiver (get tx "Destination")
        ]
    (is (not
          (some #{sender} (socket/parse-payment (get btx "tx") meta true))))
    (is (not
          (some #{receiver} (socket/parse-payment (get btx "tx") meta true))))
    ))

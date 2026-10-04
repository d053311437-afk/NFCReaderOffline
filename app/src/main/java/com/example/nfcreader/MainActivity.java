package com.example.nfcreader;

import android.app.*;
import android.os.*;
import android.nfc.*;
import android.widget.*;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
  NfcAdapter nfc; TextView status,result; Switch vibrate,raw;
  @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main); status=findViewById(R.id.status); result=findViewById(R.id.result); vibrate=findViewById(R.id.vibrate); raw=findViewById(R.id.raw); nfc=NfcAdapter.getDefaultAdapter(this); if(nfc==null) status.setText("במכשיר הזה לא נמצא NFC"); }
  @Override protected void onResume(){ super.onResume(); if(nfc!=null)nfc.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_NFC_BARCODE,null); }
  @Override protected void onPause(){ super.onPause(); if(nfc!=null)nfc.disableReaderMode(this); }
  public void onTagDiscovered(Tag tag){ String[] tech=tag.getTechList(); String type=classify(tech); String id=hex(tag.getId()); runOnUiThread(()->{ status.setText("זוהה: "+type); String s="סוג: "+type+"\nטכנולוגיות: "+String.join(", ",shortTech(tech)); if(raw.isChecked()) s += "\nUID: "+id; result.setText(s); if(vibrate.isChecked() && Build.VERSION.SDK_INT>=26) ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(45,90)); }); }
  String classify(String[] t){ String s=String.join(" ",t); if(s.contains("IsoDep")) return "כרטיס חכם ISO-DEP"; if(s.contains("Ndef")) return "תג NFC / NDEF"; if(s.contains("MifareClassic")) return "MIFARE Classic"; if(s.contains("MifareUltralight")) return "MIFARE Ultralight"; if(s.contains("NfcV")) return "NFC-V"; return "NFC לא מזוהה"; }
  String[] shortTech(String[] a){ String[] o=new String[a.length]; for(int i=0;i<a.length;i++)o[i]=a[i].substring(a[i].lastIndexOf('.')+1); return o; }
  String hex(byte[] b){ StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02X",x)); return s.toString(); }
}

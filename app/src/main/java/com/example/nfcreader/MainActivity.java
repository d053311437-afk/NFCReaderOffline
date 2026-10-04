package com.example.nfcreader;

import android.app.*;
import android.os.*;
import android.nfc.*;
import android.nfc.tech.*;
import android.media.*;
import android.content.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
  NfcAdapter nfc; TextView status,result,nfcState; Switch vibrate,raw,sound; Button copy;
  @Override public void onCreate(Bundle b){
    super.onCreate(b); setContentView(R.layout.activity_main);
    status=findViewById(R.id.status); result=findViewById(R.id.result); nfcState=findViewById(R.id.nfcState);
    vibrate=findViewById(R.id.vibrate); raw=findViewById(R.id.raw); sound=findViewById(R.id.sound); copy=findViewById(R.id.copy);
    nfc=NfcAdapter.getDefaultAdapter(this); updateState();
    copy.setOnClickListener(v->{ ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NFC",result.getText())); Toast.makeText(this,"הועתק",Toast.LENGTH_SHORT).show(); });
  }
  void updateState(){ if(nfc==null){status.setText("אין NFC במכשיר");nfcState.setText("לא נתמך");} else if(!nfc.isEnabled()){status.setText("יש להפעיל NFC");nfcState.setText("NFC כבוי");} else nfcState.setText("NFC פעיל ומוכן"); }
  @Override protected void onResume(){ super.onResume(); updateState(); if(nfc!=null&&nfc.isEnabled()) nfc.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_NFC_BARCODE,null); }
  @Override protected void onPause(){ super.onPause(); if(nfc!=null)nfc.disableReaderMode(this); }
  public void onTagDiscovered(Tag tag){
    String[] tech=tag.getTechList(); String type=classify(tech); String id=hex(tag.getId()); String ndef=readNdef(tag);
    runOnUiThread(()->{
      status.setText("זוהה בהצלחה"); nfcState.setText(type);
      StringBuilder s=new StringBuilder("סוג: ").append(type).append("\nטכנולוגיות: ").append(String.join(", ",shortTech(tech)));
      if(!ndef.isEmpty()) s.append("\n\nתוכן NDEF:\n").append(ndef);
      if(raw.isChecked()) s.append("\n\nUID: ").append(id).append("\nUID bytes: ").append(tag.getId()==null?0:tag.getId().length);
      result.setText(s.toString());
      if(vibrate.isChecked()&&Build.VERSION.SDK_INT>=26)((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(55,100));
      if(sound.isChecked()) new ToneGenerator(AudioManager.STREAM_NOTIFICATION,55).startTone(ToneGenerator.TONE_PROP_BEEP,100);
    });
  }
  String readNdef(Tag tag){ try{ Ndef n=Ndef.get(tag); if(n==null)return ""; NdefMessage m=n.getCachedNdefMessage(); if(m==null)return ""; StringBuilder out=new StringBuilder(); for(NdefRecord r:m.getRecords()){ android.net.Uri u=r.toUri(); if(u!=null) out.append(u).append("\n"); else out.append("Record: TNF ").append(r.getTnf()).append(", ").append(r.getPayload().length).append(" bytes\n"); } return out.toString().trim(); }catch(Exception e){return "";} }
  String classify(String[] t){ String s=String.join(" ",t); if(s.contains("IsoDep"))return "כרטיס חכם ISO-DEP"; if(s.contains("Ndef"))return "תג NDEF"; if(s.contains("MifareClassic"))return "MIFARE Classic"; if(s.contains("MifareUltralight"))return "MIFARE Ultralight"; if(s.contains("NfcV"))return "NFC-V"; if(s.contains("NfcF"))return "NFC-F"; return "תג NFC"; }
  String[] shortTech(String[] a){ String[] o=new String[a.length]; for(int i=0;i<a.length;i++)o[i]=a[i].substring(a[i].lastIndexOf('.')+1); return o; }
  String hex(byte[] b){ if(b==null)return "לא זמין"; StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02X",x)); return s.toString(); }
}
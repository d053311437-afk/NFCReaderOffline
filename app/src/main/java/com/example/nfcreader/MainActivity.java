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
    String[] tech=tag.getTechList(); String type=classify(tech); String id=hex(tag.getId()); String ndef=IsoDep.get(tag)==null?readNdef(tag):""; String payment=readPaymentDirectory(tag);
    runOnUiThread(()->{
      status.setText("זוהה בהצלחה"); nfcState.setText(type);
      StringBuilder s=new StringBuilder("סוג: ").append(type).append("\nטכנולוגיות: ").append(String.join(", ",shortTech(tech)));
      if(!payment.isEmpty()) s.append("\n\nבדיקת כרטיס תשלום:\n").append(payment);
      if(!ndef.isEmpty()) s.append("\n\nתוכן NDEF:\n").append(ndef); else if(payment.isEmpty() && IsoDep.get(tag)==null) s.append("\n\nלא נמצא מידע NDEF פתוח לקריאה. כרטיסים חכמים כגון רב־קו, אשראי וקופת חולים עשויים לדרוש פרוטוקול ייעודי והרשאה. זיהוי NFC לבדו אינו מאפשר הצגת יתרות, חיובים או מידע רפואי.");
      if(type.equals("כרטיס חכם ISO-DEP")) s.append("\n\nזוהה ממשק כרטיס חכם (ISO-DEP). סוג הכרטיס המדויק אינו ניתן לקביעה מטכנולוגיית NFC בלבד.");
      if(raw.isChecked() && IsoDep.get(tag)==null) s.append("\n\nUID: ").append(id).append("\nUID bytes: ").append(tag.getId()==null?0:tag.getId().length);
      result.setText(s.toString());
      if(vibrate.isChecked()&&Build.VERSION.SDK_INT>=26)((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(55,100));
      if(sound.isChecked()) new ToneGenerator(AudioManager.STREAM_NOTIFICATION,55).startTone(ToneGenerator.TONE_PROP_BEEP,100);
    });
  }
  String readNdef(Tag tag){
    try {
      Ndef n=Ndef.get(tag);
      if(n==null) return "";
      NdefMessage m=n.getCachedNdefMessage();
      if(m==null) {
        try { n.connect(); m=n.getNdefMessage(); }
        finally { try { n.close(); } catch(Exception ignored) {} }
      }
      if(m==null) return "";
      StringBuilder out=new StringBuilder();
      for(NdefRecord r:m.getRecords()){
        if(r.getTnf()==NdefRecord.TNF_WELL_KNOWN && Arrays.equals(r.getType(),NdefRecord.RTD_TEXT)){
          byte[] payload=r.getPayload();
          if(payload.length<1) continue;
          int languageLength=payload[0]&0x3F;
          if(1+languageLength>payload.length) continue;
          String encoding=(payload[0]&0x80)==0?"UTF-8":"UTF-16";
          out.append(new String(payload,1+languageLength,payload.length-1-languageLength,encoding)).append("\\n");
        } else {
          android.net.Uri u=r.toUri();
          if(u!=null) out.append(u).append("\\n");
          else out.append("רשומה מסוג ").append(r.getTnf()).append(" (").append(r.getPayload().length).append(" בתים)\\n");
        }
      }
      return out.toString().trim();
    }catch(Exception e){ return ""; }
  }
  String readPaymentDirectory(Tag tag) {
    IsoDep iso=IsoDep.get(tag);
    if(iso==null) return "";
    try {
      iso.setTimeout(2500);
      iso.connect();
      // EMV contactless PPSE: directory selection only. Never request card numbers or transaction records.
      byte[] command=new byte[]{0x00,(byte)0xA4,0x04,0x00,0x0E,
        0x32,0x50,0x41,0x59,0x2E,0x53,0x59,0x53,0x2E,0x44,0x44,0x46,0x30,0x31,0x00};
      byte[] response=iso.transceive(command);
      if(response.length<2) return "הכרטיס לא החזיר תשובה תקינה.";
      int sw=((response[response.length-2]&255)<<8)|(response[response.length-1]&255);
      if(sw==0x9000) return "זוהה ממשק תשלום EMV ללא מגע.\\nהקריאה הצליחה. אין אפשרות לזהות בוודאות את חברת ההנפקה (ישראכרט) מתוך הבדיקה הזו.\\nמטעמי פרטיות לא נקראים מספר הכרטיס, פרטי עסקאות או נתונים אישיים.\\nחיובים ויתרות אינם שמורים בממשק NFC של הכרטיס.";
      if(sw==0x6A82) return "לא נמצאה ספריית תשלום EMV (PPSE). ייתכן שהכרטיס אינו תומך בממשק זה.";
      return "הכרטיס הגיב, אך לא אישר את בדיקת ספריית התשלום. קוד תגובה: "+String.format(java.util.Locale.US,"%04X",sw);
    } catch(Exception e) {
      return "לא ניתן להשלים את בדיקת ממשק התשלום. נסה להחזיק את הכרטיס יציב כמה שניות.";
    } finally {
      try { if(iso.isConnected()) iso.close(); } catch(Exception ignored) {}
    }
  }
  String classify(String[] t){ String s=String.join(" ",t); if(s.contains("IsoDep"))return "כרטיס חכם ISO-DEP"; if(s.contains("Ndef"))return "תג NDEF"; if(s.contains("MifareClassic"))return "MIFARE Classic"; if(s.contains("MifareUltralight"))return "MIFARE Ultralight"; if(s.contains("NfcV"))return "NFC-V"; if(s.contains("NfcF"))return "NFC-F"; return "תג NFC"; }
  String[] shortTech(String[] a){ String[] o=new String[a.length]; for(int i=0;i<a.length;i++)o[i]=a[i].substring(a[i].lastIndexOf('.')+1); return o; }
  String hex(byte[] b){ if(b==null)return "לא זמין"; StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02X",x)); return s.toString(); }
}
package com.bubbleladder.triplehedgev32;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT=9301,REQ_IMPORT=9302,REQ_NOTI=9303;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ExecutorService ex=Executors.newSingleThreadExecutor();

    private TextView countdown,bgState,status,nextRound,triple,exclude,grade,candidates,engines,backtest,live,profit,recent;
    private EditText stake,odds;
    private CheckBox background;
    private Button refresh,saveSetting,backup,restore,reset;

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){reloadAsync();}
    };
    private final Runnable countdownTask=new Runnable(){
        @Override public void run(){countdown.setText(TripleCore.countdownText());h.postDelayed(this,1000L);}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(buildUi());
        loadSettings();bindActions();registerUpdates();requestNotificationPermissionIfNeeded();
        h.post(countdownTask);
        if(TripleCore.prefs(this).getBoolean(TripleCore.K_AUTO,true))startAutoService();
        reloadAsync();
    }

    private View buildUi(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(16),dp(14),dp(30));root.setBackgroundColor(Color.rgb(7,19,26));sv.addView(root);

        root.addView(tv("보글사다리3 · 삼치기 Hedge V3.2",24,Color.WHITE,true));
        TextView sub=tv("약/연속 재판정 · 5엔진 합의 · V1/V3.1 동일상황 A/B · 최근30 자동감점",12,Color.rgb(110,231,183),false);
        sub.setPadding(0,dp(4),0,dp(14));root.addView(sub);

        LinearLayout clock=card();
        clock.addView(tv("다음 추첨까지",12,Color.rgb(148,163,184),false));
        countdown=tv("--:--",38,Color.rgb(56,189,248),true);clock.addView(countdown);
        bgState=tv("백그라운드 상태 확인 중",12,Color.rgb(203,213,225),false);bgState.setPadding(0,dp(4),0,0);clock.addView(bgState);
        root.addView(clock);

        LinearLayout ctrl=card();
        refresh=button("🔄 지금 추첨/4확률 분석 실행",Color.rgb(5,150,105));ctrl.addView(refresh,new LinearLayout.LayoutParams(-1,dp(54)));
        background=new CheckBox(this);background.setText("백그라운드 자동추첨 ON");background.setTextColor(Color.WHITE);background.setTextSize(15);background.setPadding(0,dp(8),0,0);ctrl.addView(background);
        status=tv("조회 준비",12,Color.rgb(203,213,225),false);status.setPadding(0,dp(6),0,0);ctrl.addView(status);root.addView(ctrl);

        LinearLayout hero=card();
        hero.addView(tv("다음 회차 V3.2 삼치기",12,Color.GRAY,false));
        nextRound=tv("-",15,Color.WHITE,true);hero.addView(nextRound);
        triple=tv("분석 대기",30,Color.rgb(52,211,153),true);triple.setPadding(0,dp(7),0,dp(5));hero.addView(triple);
        exclude=tv("제외조합 -",16,Color.rgb(248,113,113),true);hero.addView(exclude);
        grade=tv("V3.2 신호 -",15,Color.rgb(253,224,71),true);grade.setPadding(0,dp(4),0,0);hero.addView(grade);
        TextView always=tv("● PASS 없음 · 기본 4확률 + 약/연속 재판정 → 최종 1개 제외",12,Color.rgb(110,231,183),true);always.setPadding(0,dp(10),0,0);hero.addView(always);
        TextView caveat=tv("※ 표시되는 4개 값은 모델의 상대점수입니다. 실제 당첨확률을 보장하지 않습니다.",11,Color.GRAY,false);caveat.setPadding(0,dp(7),0,0);hero.addView(caveat);
        root.addView(hero);

        LinearLayout cc=card();cc.addView(section("4개 조합 최종 경쟁 · 낮은 발생점수 1개 제외"));
        candidates=tv("-",15,Color.WHITE,false);candidates.setLineSpacing(0,1.3f);cc.addView(candidates);root.addView(cc);

        LinearLayout ec=card();ec.addView(section("V3.2 상황별 Hedge · 엔진 제외성공률"));
        engines=tv("-",13,Color.rgb(226,232,240),false);engines.setLineSpacing(0,1.25f);ec.addView(engines);root.addView(ec);

        LinearLayout bc=card();bc.addView(section("미래누설 없는 순차 재현검증"));
        backtest=tv("-",14,Color.WHITE,false);backtest.setLineSpacing(0,1.28f);bc.addView(backtest);
        live=tv("실전 기록 · 아직 없음",14,Color.rgb(125,211,252),true);live.setPadding(0,dp(10),0,0);bc.addView(live);root.addView(bc);

        LinearLayout pc=card();pc.addView(section("고정배팅 · 수익 계산"));
        LinearLayout ir=new LinearLayout(this);ir.setOrientation(LinearLayout.HORIZONTAL);
        stake=input("5000");stake.setHint("1개 배팅금액");stake.setInputType(InputType.TYPE_CLASS_NUMBER);
        odds=input("1.95");odds.setHint("배당");odds.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ir.addView(stake,new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams olp=new LinearLayout.LayoutParams(0,dp(52),1);olp.setMargins(dp(8),0,0,0);ir.addView(odds,olp);pc.addView(ir);
        saveSetting=button("설정 저장 · 최소 5,000원",Color.rgb(30,64,175));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(48));slp.setMargins(0,dp(8),0,0);pc.addView(saveSetting,slp);
        profit=tv("-",13,Color.rgb(226,232,240),false);profit.setPadding(0,dp(10),0,0);profit.setLineSpacing(0,1.25f);pc.addView(profit);root.addView(pc);

        LinearLayout rc=card();rc.addView(section("최근 10회 결과"));recent=tv("-",14,Color.WHITE,false);recent.setLineSpacing(0,1.25f);rc.addView(recent);root.addView(rc);

        LinearLayout dc=card();dc.addView(section("데이터 백업 / V1·V2·V3.1 기록 가져오기"));
        LinearLayout dr=new LinearLayout(this);dr.setOrientation(LinearLayout.HORIZONTAL);
        backup=button("💾 V3.2 백업",Color.rgb(21,128,61));restore=button("📂 복원/가져오기",Color.rgb(109,40,217));
        dr.addView(backup,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(50),1);rp.setMargins(dp(8),0,0,0);dr.addView(restore,rp);dc.addView(dr);
        reset=button("V3.2 기록만 초기화",Color.rgb(127,29,29));LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(46));rlp.setMargins(0,dp(8),0,0);dc.addView(reset,rlp);root.addView(dc);

        root.addView(tv("균등 무작위라면 삼치기 기준 성공률은 75%입니다. V3.2는 V3.1과 같은 구간 A/B 순차검증으로 개선 여부를 확인하세요.",11,Color.GRAY,false));
        return sv;
    }

    private void bindActions(){
        refresh.setOnClickListener(v->manualSync());
        saveSetting.setOnClickListener(v->saveSettings());
        background.setOnCheckedChangeListener((v,on)->{
            TripleCore.prefs(this).edit().putBoolean(TripleCore.K_AUTO,on).apply();
            if(on)startAutoService();else stopAutoService();updateBgState();
        });
        backup.setOnClickListener(v->startExport());
        restore.setOnClickListener(v->startImport());
        reset.setOnClickListener(v->confirmReset());
    }

    private void loadSettings(){
        android.content.SharedPreferences sp=TripleCore.prefs(this);
        stake.setText(String.valueOf(Math.max(5000,sp.getInt(TripleCore.K_BASE_STAKE,5000))));
        odds.setText(String.valueOf(sp.getFloat(TripleCore.K_ODDS,1.95f)));
        background.setChecked(sp.getBoolean(TripleCore.K_AUTO,true));
        updateBgState();
    }

    private void saveSettings(){
        int s=readStake();double o=readOdds();
        TripleCore.prefs(this).edit().putInt(TripleCore.K_BASE_STAKE,s).putFloat(TripleCore.K_ODDS,(float)o).apply();
        stake.setText(String.valueOf(s));Toast.makeText(this,"설정 저장 완료",Toast.LENGTH_SHORT).show();reloadAsync();
    }

    private int readStake(){
        try{return Math.max(5000,Integer.parseInt(stake.getText().toString().trim()));}catch(Exception e){return 5000;}
    }

    private double readOdds(){
        try{return Math.max(1.01,Double.parseDouble(odds.getText().toString().trim()));}catch(Exception e){return 1.95;}
    }

    private void manualSync(){
        saveSettingsSilent();refresh.setEnabled(false);status.setText("추첨 결과 조회 + V3.2 재판정 분석 중...");
        ex.execute(()->{
            try{
                TripleCore.SyncResult sr=TripleCore.sync(this);
                h.post(()->{
                    render(sr.analysis,sr.history);
                    status.setText("● 완료 · "+new SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new Date()));
                    status.setTextColor(Color.rgb(52,211,153));refresh.setEnabled(true);
                });
            }catch(Exception e){
                h.post(()->{
                    status.setText("조회 실패: "+e.getMessage());status.setTextColor(Color.rgb(248,113,113));refresh.setEnabled(true);
                });
            }
        });
    }

    private void saveSettingsSilent(){
        TripleCore.prefs(this).edit().putInt(TripleCore.K_BASE_STAKE,readStake()).putFloat(TripleCore.K_ODDS,(float)readOdds()).apply();
    }

    private void reloadAsync(){
        ex.execute(()->{
            List<TripleCore.Result>d=TripleCore.load(this);
            TripleCore.Analysis a=d.isEmpty()?null:TripleCore.analyze(d);
            h.post(()->{
                if(a!=null)render(a,d);else status.setText("데이터 없음 · 지금 추첨/분석 실행을 눌러주세요.");
                updateBgState();
            });
        });
    }

    private void render(TripleCore.Analysis a,List<TripleCore.Result>d){
        if(a==null||d==null||d.isEmpty())return;
        TripleCore.Result last=d.get(0);
        nextRound.setText(last.round<480?last.date+" · "+(last.round+1)+"회":"다음날 · 1회");
        triple.setText(a.triple);
        exclude.setText("최종 제외: "+TripleCore.COMBO[a.exclude]+" · 기본 V3.1 최저: "+TripleCore.COMBO[a.baseExclude]);
        grade.setText("V3.2 "+gradeIcon(a.grade)+" "+a.grade+" · "+a.decisionMode+" · 격차 "+String.format(Locale.KOREA,"%.1f",a.scoreGap)+" · "+a.context);

        StringBuilder cr=new StringBuilder();
        for(int i=0;i<a.rank.size();i++){
            int k=a.rank.get(i);
            cr.append(i+1).append("순위  ").append(TripleCore.COMBO[k])
                    .append("   발생점수 ").append(TripleCore.pct(a.occurrence[k]))
                    .append("   제외점수 ").append(String.format(Locale.KOREA,"%.1f",a.excludeScore[k]));
            if(k==a.baseExclude)cr.append("   · 기본최저");
            if(k==a.exclude)cr.append("   ← V3.2 최종제외");
            if(i<a.rank.size()-1)cr.append("\n");
        }
        cr.append("\n\n유사상황 ").append(a.similarMatches).append("건 · 최종 반영 ")
                .append(String.format(Locale.KOREA,"%.0f%%",a.similarWeight*100))
                .append(" (최대 18%)")
                .append("\n\n[V3.2 재판정]")
                .append("\n기본 V3.1 제외: ").append(TripleCore.COMBO[a.baseExclude])
                .append(" · V1 제외: ").append(TripleCore.COMBO[a.v1Exclude])
                .append("\n5엔진 최다합의: ").append(a.rescueVotes).append("/5")
                .append(" · 최종: ").append(a.decisionMode)
                .append("\n동일상황 보정성적 V3.1 ").append(a.baseContextN).append("회 ").append(TripleCore.pct(a.baseContextRate))
                .append(" · V1 ").append(a.v1ContextN).append("회 ").append(TripleCore.pct(a.v1ContextRate));
        candidates.setText(cr);

        StringBuilder es=new StringBuilder();
        es.append("현재 상황: ").append(a.context).append(" · 표본보호 유지\n")
                .append("약/연속은 5엔진 재투표 · 합의분산 시 V1/V3.1 동일상황 비교\n")
                .append("최근30 안정 엔진은 최근성적 75% 미만이면 자동 감점\n\n");
        for(int e=0;e<TripleCore.ENGINE_COUNT;e++){
            TripleCore.Perf g=a.enginePerf[e],c=a.contextPerf[e];
            es.append("• ").append(TripleCore.ENGINE[e]).append("\n")
                    .append("  전체 ").append(g.hit).append("/").append(g.n).append(g.n>0?" "+TripleCore.pct(g.rate()):"")
                    .append(" · 최근 ").append(g.rhit).append("/").append(g.rn).append(g.rn>0?" "+TripleCore.pct(g.recentRate()):"");
            if(e==6){
                es.append("\n  → 단독 가중 금지 · 유사상황은 10~18% 범위 보조판정");
            }else{
                es.append("\n  현재상황 ").append(c.hit).append("/").append(c.n).append(c.n>0?" "+TripleCore.pct(c.rate()):"")
                        .append(" · ").append(TripleCore.contextSampleLabel(c.n))
                        .append(" · 최종가중 ").append(String.format(Locale.KOREA,"%.2f",g.weight));
            }
            if(e<TripleCore.ENGINE_COUNT-1)es.append("\n\n");
        }
        engines.setText(es.toString());

        TripleCore.HedgeStats ht=a.hedge;
        TripleCore.CompareStats cp=a.compare;
        backtest.setText(
                "V3.2 전체: "+ht.hit+"/"+ht.n+(ht.n>0?" = "+TripleCore.pct(ht.rate()):"")+
                "\nV3.2 최근 50회: "+ht.rhit+"/"+ht.rn+(ht.rn>0?" = "+TripleCore.pct(ht.recentRate()):"")+
                "\nV3.1 최근 50회: "+ht.baseRHit+"/"+ht.baseRN+(ht.baseRN>0?" = "+TripleCore.pct((double)ht.baseRHit/ht.baseRN):"")+
                "\n\n[V1 · V2 · V3.1 · V3.2 같은 구간 비교]"+
                "\nV1 방식: "+cp.v1Hit+"/"+cp.v1N+" = "+TripleCore.pct(cp.v1Rate())+
                "\nV2 방식: "+cp.v2Hit+"/"+cp.v2N+" = "+TripleCore.pct(cp.v2Rate())+
                "\nV3.1 방식: "+cp.v3Hit+"/"+cp.v3N+" = "+TripleCore.pct(cp.v3Rate())+
                "\nV3.2 방식: "+cp.v32Hit+"/"+cp.v32N+" = "+TripleCore.pct(cp.v32Rate())+
                "\n\n[제외 1·2위 격차별 V3.2 성공률]"+
                "\n0~5: "+bucketStat(ht.gapHit[0],ht.gapN[0])+
                "\n5~10: "+bucketStat(ht.gapHit[1],ht.gapN[1])+
                "\n10~15: "+bucketStat(ht.gapHit[2],ht.gapN[2])+
                "\n15+: "+bucketStat(ht.gapHit[3],ht.gapN[3])+
                "\n\n[상황별 V3.2 성공률]"+
                "\n안정: "+bucketStat(ht.contextHit[0],ht.contextN[0])+
                "\n급변: "+bucketStat(ht.contextHit[1],ht.contextN[1])+
                "\n연속: "+bucketStat(ht.contextHit[2],ht.contextN[2])+
                "\n쏠림: "+bucketStat(ht.contextHit[3],ht.contextN[3])+
                "\n\n[재보정 등급별]"+
                "\n🔥 강: "+gradeStat(ht,2)+
                "\n🟡 보통: "+gradeStat(ht,1)+
                "\n⚠️ 약: "+gradeStat(ht,0)+
                "\n\n균등 무작위 기준: 75.0% · 저장결과 "+d.size()+"회"
        );

        android.content.SharedPreferences sp=TripleCore.prefs(this);
        int n=sp.getInt(TripleCore.K_LIVE_TOTAL,0),hit=sp.getInt(TripleCore.K_LIVE_SUCCESS,0);
        double lp=Double.longBitsToDouble(sp.getLong(TripleCore.K_LIVE_PROFIT,Double.doubleToLongBits(0)));
        live.setText("V3.2 실전 삼치기 · "+(n>0?hit+"/"+n+" = "+TripleCore.pct((double)hit/n)+" · 누적 "+TripleCore.signed(lp):"아직 없음"));

        int s=readStake();double o=readOdds();
        double bt=ht.hit*TripleCore.successProfit(s,o)-(ht.n-ht.hit)*3.0*s;
        profit.setText(
                "1회 총 배팅: "+TripleCore.money(3.0*s)+" ("+TripleCore.money(s)+" × 3개)"+
                "\n삼치기 성공(2/3): "+TripleCore.signed(TripleCore.successProfit(s,o))+
                "\n제외조합 출현(0/3): "+TripleCore.signed(-3.0*s)+
                "\n손익분기 성공률: "+TripleCore.pct(TripleCore.breakEven(o))+
                "\nV3.2 순차검증 가상손익: "+TripleCore.signed(bt)
        );

        StringBuilder rr=new StringBuilder();
        for(int i=0;i<Math.min(10,d.size());i++){
            TripleCore.Result r=d.get(i);
            rr.append(i==0?"최신  ":"      ").append(r.date).append(" - ").append(r.round).append(" · ").append(TripleCore.COMBO[r.combo]);
            if(i<Math.min(10,d.size())-1)rr.append("\n");
        }
        recent.setText(rr);
        updateBgState();
    }

    private String gradeStat(TripleCore.HedgeStats h,int i){
        return h.gradeN[i]==0?"-":h.gradeHit[i]+"/"+h.gradeN[i]+" = "+TripleCore.pct((double)h.gradeHit[i]/h.gradeN[i]);
    }
    private String bucketStat(int hit,int n){return n==0?"-":hit+"/"+n+" = "+TripleCore.pct((double)hit/n);}
    private String gradeIcon(String g){return "강".equals(g)?"🔥":"보통".equals(g)?"🟡":"⚠️";}

    private void startAutoService(){
        try{
            Intent i=new Intent(this,AutoDrawService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception e){status.setText("백그라운드 시작 실패: "+e.getMessage());}
        updateBgState();
    }

    private void stopAutoService(){stopService(new Intent(this,AutoDrawService.class));updateBgState();}

    private void updateBgState(){
        boolean on=TripleCore.prefs(this).getBoolean(TripleCore.K_AUTO,true);
        bgState.setText(on?"● 백그라운드 ON · 추첨 후 자동조회/채점/V3.2 재계산":"○ 백그라운드 OFF");
        bgState.setTextColor(on?Color.rgb(52,211,153):Color.GRAY);
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTI);
    }

    private void registerUpdates(){
        IntentFilter f=new IntentFilter(TripleCore.ACTION_UPDATED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
    }

    private void startExport(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"BubbleTripleHedgeV32_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.KOREA).format(new Date())+".json");
        startActivityForResult(i,REQ_EXPORT);
    }

    private void startImport(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_IMPORT);
    }

    private void exportUri(Uri u){
        try{
            OutputStream o=getContentResolver().openOutputStream(u);
            if(o==null)throw new Exception("파일 열기 실패");
            o.write(TripleCore.backup(this).toString(2).getBytes("UTF-8"));o.close();
            Toast.makeText(this,"V3.2 백업 완료",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"백업 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void importUri(Uri u){
        try{
            InputStream is=getContentResolver().openInputStream(u);
            BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));
            StringBuilder sb=new StringBuilder();String l;while((l=br.readLine())!=null)sb.append(l);br.close();
            TripleCore.restore(this,new JSONObject(sb.toString()));
            Toast.makeText(this,"복원/가져오기 완료",Toast.LENGTH_LONG).show();recreate();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("복원 실패").setMessage(e.getMessage()).setPositiveButton("확인",null).show();}
    }

    @Override protected void onActivityResult(int rc,int res,Intent data){
        super.onActivityResult(rc,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();
        if(rc==REQ_EXPORT)exportUri(u);
        else if(rc==REQ_IMPORT)new AlertDialog.Builder(this).setTitle("데이터 가져오기")
                .setMessage("V3.2 백업은 전체 복원하고, V1/V2/V3.1 백업은 결과 기록과 기본설정만 가져옵니다.")
                .setNegativeButton("취소",null).setPositiveButton("가져오기",(d,w)->importUri(u)).show();
    }

    private void confirmReset(){
        new AlertDialog.Builder(this).setTitle("V3.2 기록 초기화")
                .setMessage("V3.2의 누적 결과·실전 적중·수익 기록을 삭제합니다. V1/V2/V3.1 앱에는 영향이 없습니다.")
                .setNegativeButton("취소",null)
                .setPositiveButton("초기화",(d,w)->{
                    boolean auto=TripleCore.prefs(this).getBoolean(TripleCore.K_AUTO,true);
                    TripleCore.prefs(this).edit().clear().putBoolean(TripleCore.K_AUTO,auto).apply();recreate();
                }).show();
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(round(Color.rgb(17,24,39),18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(12));c.setLayoutParams(lp);return c;
    }
    private TextView section(String s){TextView v=tv(s,17,Color.WHITE,true);v.setPadding(0,0,0,dp(9));return v;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s,int c){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(c,14));return b;}
    private EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setSingleLine(true);e.setPadding(dp(10),0,dp(10),0);e.setBackground(round(Color.rgb(30,41,59),12));return e;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override protected void onDestroy(){
        h.removeCallbacksAndMessages(null);try{unregisterReceiver(receiver);}catch(Exception ignored){}
        ex.shutdownNow();super.onDestroy();
    }
}

package com.bubbleladder.triplehedgev33;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public final class TripleCore {
    private TripleCore() {}

    public static final String API = "https://api.bepick.io/game/bubble_ladder3";
    public static final String PREF = "bubble_triple_hedge_v33";
    public static final String ACTION_UPDATED = "com.bubbleladder.triplehedgev33.TRIPLE_UPDATED";

    public static final String K_HISTORY="history", K_PENDING_IDX="pending_idx_v33", K_PENDING_EXCLUDE="pending_exclude_v33",
            K_PENDING_STAKE="pending_stake_v33", K_PENDING_ODDS="pending_odds_v33", K_PENDING_GRADE="pending_grade_v33",
            K_LIVE_TOTAL="live_total_v33", K_LIVE_SUCCESS="live_success_v33", K_LIVE_PROFIT="live_profit_v33",
            K_BASE_STAKE="base_stake_v33", K_ODDS="odds_v33", K_RECORDS="records_v33", K_AUTO="auto_enabled_v33",
            K_LAST_EXCLUDE="last_exclude_v33", K_LAST_TRIPLE="last_triple_v33", K_LAST_GRADE="last_grade_v33",
            K_LAST_GAP="last_gap_v33", K_LAST_CONTEXT="last_context_v33", K_LAST_SYNC="last_sync_v33";

    public static final int MAX_HISTORY=5000, BT_LIMIT=650, ENGINE_COUNT=9, CONTEXT_COUNT=4;
    private static final int STRAT_BASE=0, STRAT_CONSENSUS=1, STRAT_V1=2, STRAT_COUNT=3;
    private static final int[] RESCUE_ENGINES={0,1,7,8,6};
    public static final int LOCAL_BONUS_MIN=8, LOCAL_BONUS_FULL=16, CONTEXT_BIAS_FULL=20;
    public static final String[] COMBO={"","좌3짝","좌4홀","우3홀","우4짝"};
    public static final String[] ENGINE={
            "최근8 가중","최근15 가중","최근30 안정",
            "4상태 Markov-1","4상태 Markov-2","연속상태 조건",
            "유사상황 검색","Binary 2-Bit","Regime Adaptive"
    };
    public static final String[] CONTEXT={"안정","급변","연속","쏠림"};
    // Similar search(6) is held out of the family blend and can only add a capped 10~18%.
    private static final int[] ENGINE_FAMILY={0,0,0,1,1,1,-1,2,2};

    public static SharedPreferences prefs(Context c){ return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static final class Result { public long idx; public String date; public int round,combo; }

    public static final class Perf {
        public int n,hit,rn,rhit;
        public double weight;
        public double rate(){return n==0?.75:(double)hit/n;}
        public double recentRate(){return rn==0?rate():(double)rhit/rn;}
    }

    public static final class HedgeStats {
        public int n,hit,rn,rhit;
        public int[] gradeN=new int[3],gradeHit=new int[3];
        public int[] gapN=new int[4],gapHit=new int[4];
        public int[] contextN=new int[CONTEXT_COUNT],contextHit=new int[CONTEXT_COUNT];
        public int baseN,baseHit,baseRN,baseRHit;
        public double rate(){return n==0?.75:(double)hit/n;}
        public double recentRate(){return rn==0?rate():(double)rhit/rn;}
    }

    public static final class CompareStats {
        public int v1N,v1Hit,v2N,v2Hit,v3N,v3Hit,v32N,v32Hit,v33N,v33Hit;
        public double v1Rate(){return v1N==0?.75:(double)v1Hit/v1N;}
        public double v2Rate(){return v2N==0?.75:(double)v2Hit/v2N;}
        public double v3Rate(){return v3N==0?.75:(double)v3Hit/v3N;}
        public double v32Rate(){return v32N==0?.75:(double)v32Hit/v32N;}
        public double v33Rate(){return v33N==0?.75:(double)v33Hit/v33N;}
    }

    public static final class MetaStats {
        public int agreeN,agreeHit,conflictN,conflictV31Hit,conflictV32Hit,conflictFinalHit;
        public int choose31N,choose31Hit,choose32N,choose32Hit;
        public int recentN,recent31Hit,recent32Hit;
        public int[] contextN=new int[CONTEXT_COUNT],context31Hit=new int[CONTEXT_COUNT],context32Hit=new int[CONTEXT_COUNT];
        public double agreeRate(){return agreeN==0?.75:(double)agreeHit/agreeN;}
        public double conflict31Rate(){return conflictN==0?.75:(double)conflictV31Hit/conflictN;}
        public double conflict32Rate(){return conflictN==0?.75:(double)conflictV32Hit/conflictN;}
        public double conflictFinalRate(){return conflictN==0?.75:(double)conflictFinalHit/conflictN;}
    }

    public static final class Analysis {
        public double[] occurrence=new double[5];
        public double[] excludeScore=new double[5];
        public int exclude,baseExclude,v32Exclude,contextId,similarMatches,rescueVotes,v1Exclude;
        public String triple,grade,context,decisionMode,v32DecisionMode;
        public double scoreGap,similarWeight,baseContextRate,v1ContextRate,metaV31Score,metaV32Score;
        public int baseContextN,v1ContextN,metaEvidenceN;
        public boolean metaAgreement;
        public List<Integer> rank;
        public Perf[] enginePerf,contextPerf;
        public HedgeStats hedge,hedge32;
        public MetaStats meta;
        public CompareStats compare;
    }

    public static final class SyncResult {
        public boolean newRoundResolved;
        public Analysis analysis;
        public List<Result> history;
    }

    private static final class SimilarInfo {
        double[] p;
        int matches;
    }

    private static final class RescueVote {
        int exclude,votes;
    }

    private static final class PolicyState {
        int[][][] n=new int[CONTEXT_COUNT][3][STRAT_COUNT];
        int[][][] hit=new int[CONTEXT_COUNT][3][STRAT_COUNT];
        int[][] ctxN=new int[CONTEXT_COUNT][STRAT_COUNT];
        int[][] ctxHit=new int[CONTEXT_COUNT][STRAT_COUNT];
    }

    private static final class Decision {
        int exclude,strategy;
        String mode;
        double baseRate,v1Rate;
        int baseN,v1N;
    }

    private static final class MetaState {
        int agreeN,agreeHit,conflictN,conflict31Hit,conflict32Hit,conflictFinalHit;
        int choose31N,choose31Hit,choose32N,choose32Hit;
        int[] ctxN=new int[CONTEXT_COUNT],ctx31Hit=new int[CONTEXT_COUNT],ctx32Hit=new int[CONTEXT_COUNT];
        ArrayDeque<Boolean> recent31=new ArrayDeque<>(),recent32=new ArrayDeque<>();
        int recent31Hit,recent32Hit;
    }

    private static final class MetaDecision {
        int exclude,source,evidenceN;
        String mode;
        boolean agreement;
        double v31Score,v32Score;
    }

    private static final class BacktestBundle {
        HedgeStats stats,stats32;
        PolicyState policy;
        MetaState metaState;
        MetaStats metaStats;
    }

    public static List<Result> fetch() throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000); c.setReadTimeout(12000); c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","BubbleTripleHedge/3.3");
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new Exception("API HTTP "+code);
        BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
        StringBuilder sb=new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        br.close(); c.disconnect();
        JSONObject root=new JSONObject(sb.toString());
        JSONArray arr=root.optJSONArray("data");
        if(arr==null) throw new Exception("API data 없음");
        List<Result> out=new ArrayList<>();
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i); if(o==null) continue;
            int combo=o.optInt("fd4",0); long idx=o.optLong("idx",0);
            if(idx<=0||combo<1||combo>4) continue;
            Result r=new Result();
            r.idx=idx; r.date=o.optString("date",""); r.round=o.optInt("round",0); r.combo=combo;
            out.add(r);
        }
        out.sort((a,b)->Long.compare(b.idx,a.idx));
        if(out.isEmpty()) throw new Exception("결과 없음");
        return out;
    }

    public static List<Result> load(Context c){
        List<Result> out=new ArrayList<>();
        String raw=prefs(c).getString(K_HISTORY,"");
        if(raw==null||raw.isEmpty()) return out;
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject j=a.optJSONObject(i); if(j==null) continue;
                Result r=new Result();
                r.idx=j.optLong("i"); r.date=j.optString("d"); r.round=j.optInt("r"); r.combo=j.optInt("c");
                if(r.idx>0&&r.combo>=1&&r.combo<=4) out.add(r);
            }
        }catch(Exception ignored){}
        out.sort((a,b)->Long.compare(b.idx,a.idx));
        return out;
    }

    public static void save(Context c,List<Result> list){
        try{
            JSONArray a=new JSONArray();
            for(Result r:list){
                JSONObject o=new JSONObject();
                o.put("i",r.idx);o.put("d",r.date);o.put("r",r.round);o.put("c",r.combo);a.put(o);
            }
            prefs(c).edit().putString(K_HISTORY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static List<Result> merge(List<Result>a,List<Result>b){
        TreeMap<Long,Result>m=new TreeMap<>(Collections.reverseOrder());
        for(Result r:a)m.put(r.idx,r);
        for(Result r:b)m.put(r.idx,r);
        List<Result>o=new ArrayList<>(m.values());
        if(o.size()>MAX_HISTORY)o=new ArrayList<>(o.subList(0,MAX_HISTORY));
        return o;
    }

    public static SyncResult sync(Context c) throws Exception {
        List<Result> before=load(c);
        long latestBefore=before.isEmpty()?-1:before.get(0).idx;
        List<Result> merged=merge(before,fetch());
        save(c,merged);
        boolean resolved=resolvePending(c,merged);
        Analysis a=analyze(merged);
        savePending(c,merged,a);
        prefs(c).edit().putLong(K_LAST_SYNC,System.currentTimeMillis()).apply();
        SyncResult sr=new SyncResult();
        sr.newRoundResolved=resolved||(!merged.isEmpty()&&merged.get(0).idx!=latestBefore);
        sr.analysis=a; sr.history=merged;
        return sr;
    }

    private static void savePending(Context c,List<Result>d,Analysis a){
        if(d.isEmpty()||a==null)return;
        SharedPreferences sp=prefs(c);
        long next=nextIdx(d.get(0)), existing=sp.getLong(K_PENDING_IDX,-1);
        if(existing==next||existing>0)return;
        int stake=Math.max(5000,sp.getInt(K_BASE_STAKE,5000));
        double odds=Math.max(1.01,sp.getFloat(K_ODDS,1.95f));
        sp.edit()
                .putLong(K_PENDING_IDX,next)
                .putInt(K_PENDING_EXCLUDE,a.exclude)
                .putInt(K_PENDING_STAKE,stake)
                .putFloat(K_PENDING_ODDS,(float)odds)
                .putString(K_PENDING_GRADE,a.grade)
                .putInt(K_LAST_EXCLUDE,a.exclude)
                .putString(K_LAST_TRIPLE,a.triple)
                .putString(K_LAST_GRADE,a.grade)
                .putString(K_LAST_CONTEXT,a.context)
                .putFloat(K_LAST_GAP,(float)a.scoreGap).apply();
    }

    private static boolean resolvePending(Context c,List<Result>d){
        SharedPreferences sp=prefs(c);
        long idx=sp.getLong(K_PENDING_IDX,-1);
        int exc=sp.getInt(K_PENDING_EXCLUDE,0);
        if(idx<=0||exc<1||exc>4)return false;
        Result actual=null;
        for(Result r:d)if(r.idx==idx){actual=r;break;}
        if(actual==null)return false;
        boolean ok=actual.combo!=exc;
        int s=sp.getInt(K_PENDING_STAKE,5000);
        double o=sp.getFloat(K_PENDING_ODDS,1.95f);
        String grade=sp.getString(K_PENDING_GRADE,"약");
        double pnl=ok?successProfit(s,o):-3.0*s;
        int n=sp.getInt(K_LIVE_TOTAL,0)+1;
        int hit=sp.getInt(K_LIVE_SUCCESS,0)+(ok?1:0);
        double old=Double.longBitsToDouble(sp.getLong(K_LIVE_PROFIT,Double.doubleToLongBits(0)));
        appendRecord(c,idx,exc,actual.combo,grade,ok,pnl);
        sp.edit()
                .putInt(K_LIVE_TOTAL,n).putInt(K_LIVE_SUCCESS,hit)
                .putLong(K_LIVE_PROFIT,Double.doubleToLongBits(old+pnl))
                .remove(K_PENDING_IDX).remove(K_PENDING_EXCLUDE)
                .remove(K_PENDING_STAKE).remove(K_PENDING_ODDS).remove(K_PENDING_GRADE).apply();
        return true;
    }

    private static void appendRecord(Context c,long idx,int exc,int actual,String grade,boolean ok,double pnl){
        try{
            SharedPreferences sp=prefs(c);
            JSONArray a=new JSONArray(sp.getString(K_RECORDS,"[]"));
            JSONObject o=new JSONObject();
            o.put("idx",idx);o.put("exclude",exc);o.put("actual",actual);o.put("grade",grade);o.put("ok",ok);o.put("pnl",pnl);
            a.put(o);
            JSONArray out=new JSONArray();
            for(int i=Math.max(0,a.length()-1500);i<a.length();i++)out.put(a.get(i));
            sp.edit().putString(K_RECORDS,out.toString()).apply();
        }catch(Exception ignored){}
    }

    public static Analysis analyze(List<Result> desc){
        if(desc==null||desc.isEmpty())return null;
        List<Result>asc=new ArrayList<>(desc);
        asc.sort(Comparator.comparingLong(x->x.idx));
        int end=asc.size();
        int ctx=contextId(asc,end);

        Perf[] global=enginePerf(asc,end);
        Perf[] local=contextPerf(asc,end,ctx);
        double[] weights=new double[ENGINE_COUNT];
        for(int e=0;e<ENGINE_COUNT;e++){
            if(e==6){weights[e]=0;continue;}
            weights[e]=adaptiveWeight(global[e],local[e],e,ctx);
            global[e].weight=weights[e];
            local[e].weight=weights[e];
        }

        SimilarInfo si=similarInfo(asc,end);
        double simW=similarBlendWeight(si.matches);
        double[] ens=metaPredict(asc,end,weights,si,simW);
        int baseExc=argmin(ens); // V3.1
        double gap=scoreGap(ens);

        BacktestBundle bt=hedgeTest(asc);
        double rr=bt.stats32.rn==0?.75:bt.stats32.recentRate();
        String baseGrade=classify(gap,rr,bt.stats32.rn);
        RescueVote rv=rescueVote(asc,end);
        int v1Exc=argmin(legacyV1Current(asc,end));
        Decision d32=chooseDecision(ctx,baseGrade,baseExc,rv,v1Exc,bt.policy);
        MetaDecision md=chooseMeta(ctx,baseGrade,baseExc,d32.exclude,d32.strategy,bt.policy,bt.metaState);
        String v32Grade=calibratedGrade(baseGrade,gap,ctx,d32.strategy,bt.policy);
        String finalGrade=metaGrade(v32Grade,gap,md,bt.metaState);

        CompareStats cmp=compareTest(asc,bt.stats32,bt.stats);
        Analysis a=new Analysis();
        a.occurrence=ens;
        a.exclude=md.exclude;
        a.baseExclude=baseExc;
        a.v32Exclude=d32.exclude;
        a.v1Exclude=v1Exc;
        a.rescueVotes=rv.votes;
        a.decisionMode=md.mode;
        a.v32DecisionMode=d32.mode;
        a.baseContextRate=d32.baseRate;
        a.v1ContextRate=d32.v1Rate;
        a.baseContextN=d32.baseN;
        a.v1ContextN=d32.v1N;
        a.metaV31Score=md.v31Score;
        a.metaV32Score=md.v32Score;
        a.metaEvidenceN=md.evidenceN;
        a.metaAgreement=md.agreement;
        a.triple=tripleFor(md.exclude);
        a.grade=finalGrade;
        a.contextId=ctx;
        a.context=CONTEXT[ctx];
        a.scoreGap=gap;
        a.enginePerf=global;
        a.contextPerf=local;
        a.hedge=bt.stats;
        a.hedge32=bt.stats32;
        a.meta=bt.metaStats;
        a.compare=cmp;
        a.similarMatches=si.matches;
        a.similarWeight=simW;
        for(int k=1;k<=4;k++)a.excludeScore[k]=excludeScore(ens[k]);
        a.rank=new ArrayList<>();
        for(int k=1;k<=4;k++)a.rank.add(k);
        a.rank.sort((x,y)->Double.compare(a.excludeScore[y],a.excludeScore[x]));
        return a;
    }

    private static Perf[] enginePerf(List<Result>a,int end){
        Perf[] p=new Perf[ENGINE_COUNT];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] q=new ArrayDeque[ENGINE_COUNT];
        int[] rh=new int[ENGINE_COUNT];
        for(int e=0;e<ENGINE_COUNT;e++){p[e]=new Perf();q[e]=new ArrayDeque<>();}
        int start=Math.max(18,end-BT_LIMIT);
        for(int t=start;t<end;t++){
            int actual=a.get(t).combo;
            for(int e=0;e<ENGINE_COUNT;e++){
                boolean ok=actual!=argmin(pred(a,t,e));
                p[e].n++;if(ok)p[e].hit++;
                q[e].addLast(ok);if(ok)rh[e]++;
                if(q[e].size()>60&&q[e].removeFirst())rh[e]--;
            }
        }
        for(int e=0;e<ENGINE_COUNT;e++){p[e].rn=q[e].size();p[e].rhit=rh[e];}
        return p;
    }

    private static Perf[] contextPerf(List<Result>a,int end,int targetCtx){
        Perf[] p=new Perf[ENGINE_COUNT];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] q=new ArrayDeque[ENGINE_COUNT];
        int[] rh=new int[ENGINE_COUNT];
        for(int e=0;e<ENGINE_COUNT;e++){p[e]=new Perf();q[e]=new ArrayDeque<>();}
        int start=Math.max(18,end-BT_LIMIT);
        for(int t=start;t<end;t++){
            if(contextId(a,t)!=targetCtx)continue;
            int actual=a.get(t).combo;
            for(int e=0;e<ENGINE_COUNT;e++){
                boolean ok=actual!=argmin(pred(a,t,e));
                p[e].n++;if(ok)p[e].hit++;
                q[e].addLast(ok);if(ok)rh[e]++;
                if(q[e].size()>35&&q[e].removeFirst())rh[e]--;
            }
        }
        for(int e=0;e<ENGINE_COUNT;e++){p[e].rn=q[e].size();p[e].rhit=rh[e];}
        return p;
    }

    private static double adaptiveWeight(Perf global,Perf local,int engine,int ctx){
        // V3.1 sample guard:
        // 같은 상황 2/2, 3/3 같은 작은 표본이 100%로 보여도 가중치 보너스를 주지 않는다.
        // 긍정 보너스는 8회부터 시작하고 16회에서 100% 반영한다.
        // 상황별 고정 bias도 표본이 쌓이기 전에는 잠근다.
        double ga=shrink(global.rate(),global.n,90,.75);
        double gr=shrink(global.recentRate(),global.rn,32,.75);
        double ca=shrink(local.rate(),local.n,42,.75);
        double cr=shrink(local.recentRate(),local.rn,18,.75);

        double globalReward=3.0*Math.max(0,ga-.75)+4.0*Math.max(0,gr-.75);
        double globalPenalty=7.0*Math.max(0,.75-ga)+9.0*Math.max(0,.75-gr);
        double localReward=4.5*Math.max(0,ca-.75)+5.5*Math.max(0,cr-.75);
        double localPenalty=10.0*Math.max(0,.75-ca)+12.0*Math.max(0,.75-cr);

        double globalEvidence=Math.min(1.0,global.n/(global.n+55.0));
        double localEvidence=Math.min(1.0,local.n/(local.n+28.0));

        double positiveGate=localPositiveGate(local.n);
        double negativeGate=localNegativeGate(local.n);

        double w=1.0
                +(globalReward-globalPenalty)*globalEvidence
                +localReward*localEvidence*positiveGate
                -localPenalty*localEvidence*negativeGate;

        // 기존 상황별 선호도는 '가설'로만 사용한다.
        // 같은 상황 표본이 8회 미만이면 완전히 끄고, 20회까지 서서히 연다.
        double bias=contextBias(engine,ctx);
        double biasGate=contextBiasGate(local.n);
        if(bias>1.0 && ca<=.75) biasGate=0.0;
        if(bias<1.0 && ca>=.75) biasGate=0.0;
        double effectiveBias=1.0+(bias-1.0)*biasGate;
        double finalW=w*effectiveBias;

        // V3.3: 최근30 안정 엔진은 "안정"이라는 이름만으로 버티지 못한다.
        // 최근 제외성공률이 75% 아래면 자동 감점하고, 좋아져도 단독 지배하지 않도록 상한을 둔다.
        if(engine==2){
            double r30=shrink(global.recentRate(),global.rn,18,.75);
            if(r30<.75){
                double reliability=clamp(.65+5.0*(r30-.70),.45,1.0);
                finalW*=reliability;
            }
            finalW=Math.min(finalW,1.05);
        }
        return clamp(finalW,.15,2.20);
    }

    private static double localPositiveGate(int n){
        if(n<LOCAL_BONUS_MIN)return 0.0;
        if(n>=LOCAL_BONUS_FULL)return 1.0;
        return .25+.75*(n-LOCAL_BONUS_MIN)/(double)(LOCAL_BONUS_FULL-LOCAL_BONUS_MIN);
    }

    private static double localNegativeGate(int n){
        // 나쁜 신호도 1~3회만으로 과잉 벌점을 주지 않는다.
        if(n<4)return 0.0;
        if(n>=12)return 1.0;
        return .25+.75*(n-4)/8.0;
    }

    private static double contextBiasGate(int n){
        if(n<LOCAL_BONUS_MIN)return 0.0;
        if(n>=CONTEXT_BIAS_FULL)return 1.0;
        return (n-LOCAL_BONUS_MIN)/(double)(CONTEXT_BIAS_FULL-LOCAL_BONUS_MIN);
    }

    public static String contextSampleLabel(int n){
        if(n<LOCAL_BONUS_MIN)return "표본부족 · 상황보너스 0%";
        if(n<LOCAL_BONUS_FULL){
            int pct=(int)Math.round(localPositiveGate(n)*100.0);
            return "부분반영 "+pct+"%";
        }
        if(n<CONTEXT_BIAS_FULL){
            int pct=(int)Math.round(contextBiasGate(n)*100.0);
            return "성적 100%반영 · 상황bias "+pct+"%";
        }
        return "충분표본 · 100%반영";
    }

    private static double contextBias(int e,int ctx){
        double b=1.0;
        if(ctx==0){ if(e==2)b=1.08; if(e==3)b=1.05; }
        else if(ctx==1){ if(e==0)b=1.10; if(e==1)b=1.06; if(e==8)b=1.12; if(e==2)b=.92; }
        else if(ctx==2){ if(e==4)b=1.10; if(e==5)b=1.15; if(e==3)b=1.06; }
        else if(ctx==3){ if(e==0)b=1.05; if(e==7)b=1.10; if(e==8)b=1.06; }
        return b;
    }

    private static double[] metaPredict(List<Result>a,int end,double[] weights,SimilarInfo si,double simW){
        double[][] fam=new double[3][5];
        double[] famW=new double[3], famReliability=new double[3];

        for(int e=0;e<ENGINE_COUNT;e++){
            int f=ENGINE_FAMILY[e];
            if(f<0)continue;
            double w=Math.max(.01,weights[e]);
            double[] p=pred(a,end,e);
            famW[f]+=w;
            famReliability[f]+=w;
            for(int k=1;k<=4;k++)fam[f][k]+=p[k]*w;
        }

        double[] base=new double[5];
        double totalFamily=0;
        for(int f=0;f<3;f++){
            if(famW[f]<=0){
                for(int k=1;k<=4;k++)fam[f][k]=.25;
                famReliability[f]=1;
            }else{
                for(int k=1;k<=4;k++)fam[f][k]/=famW[f];
                norm(fam[f]);
                famReliability[f]=clamp(famReliability[f]/Math.max(1, familyEngineCount(f)),.55,1.65);
            }
            totalFamily+=famReliability[f];
            for(int k=1;k<=4;k++)base[k]+=fam[f][k]*famReliability[f];
        }
        for(int k=1;k<=4;k++)base[k]/=Math.max(.0001,totalFamily);
        norm(base);

        if(simW>0&&si!=null&&si.p!=null){
            for(int k=1;k<=4;k++)base[k]=base[k]*(1-simW)+si.p[k]*simW;
            norm(base);
        }
        return base;
    }

    private static int familyEngineCount(int f){
        if(f==0)return 3;
        if(f==1)return 3;
        return 2;
    }

    private static BacktestBundle hedgeTest(List<Result>a){
        HedgeStats h33=new HedgeStats(),h32=new HedgeStats();
        PolicyState ps=new PolicyState();
        MetaState ms=new MetaState();
        int end=a.size(),start=Math.max(24,end-BT_LIMIT);

        int[] en=new int[ENGINE_COUNT],eh=new int[ENGINE_COUNT],erh=new int[ENGINE_COUNT];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] eq=new ArrayDeque[ENGINE_COUNT];
        for(int e=0;e<ENGINE_COUNT;e++)eq[e]=new ArrayDeque<>();

        int[][] cn=new int[CONTEXT_COUNT][ENGINE_COUNT],ch=new int[CONTEXT_COUNT][ENGINE_COUNT],crh=new int[CONTEXT_COUNT][ENGINE_COUNT];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[][] cq=new ArrayDeque[CONTEXT_COUNT][ENGINE_COUNT];
        for(int c=0;c<CONTEXT_COUNT;c++)for(int e=0;e<ENGINE_COUNT;e++)cq[c][e]=new ArrayDeque<>();

        final int V1EC=8;
        int[] v1n=new int[V1EC],v1hit=new int[V1EC],v1rh=new int[V1EC];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] v1q=new ArrayDeque[V1EC];
        for(int e=0;e<V1EC;e++)v1q[e]=new ArrayDeque<>();

        ArrayDeque<Boolean> q33=new ArrayDeque<>(),q32=new ArrayDeque<>(),q31=new ArrayDeque<>();
        int r33=0,r32=0,r31=0;

        for(int t=start;t<end;t++){
            int ctx=contextId(a,t);
            double[] weights=new double[ENGINE_COUNT];
            for(int e=0;e<ENGINE_COUNT;e++){
                if(e==6){weights[e]=0;continue;}
                Perf g=perfFrom(en[e],eh[e],eq[e].size(),erh[e]);
                Perf l=perfFrom(cn[ctx][e],ch[ctx][e],cq[ctx][e].size(),crh[ctx][e]);
                weights[e]=adaptiveWeight(g,l,e,ctx);
            }

            SimilarInfo si=similarInfo(a,t);
            double[] ens=metaPredict(a,t,weights,si,similarBlendWeight(si.matches));
            int actual=a.get(t).combo,baseExc=argmin(ens);
            boolean ok31=actual!=baseExc;
            double rr32=q32.isEmpty()?.75:(double)r32/q32.size();
            double gap=scoreGap(ens);
            String baseGrade=classify(gap,rr32,q32.size());

            RescueVote rv=rescueVote(a,t);
            int v1Exc=argmin(legacyV1Forecast(a,t,v1n,v1hit,v1q,v1rh));
            Decision d32=chooseDecision(ctx,baseGrade,baseExc,rv,v1Exc,ps);
            boolean ok32=actual!=d32.exclude;

            MetaDecision md=chooseMeta(ctx,baseGrade,baseExc,d32.exclude,d32.strategy,ps,ms);
            boolean ok33=actual!=md.exclude;
            String grade32=calibratedGrade(baseGrade,gap,ctx,d32.strategy,ps);
            String grade33=metaGrade(grade32,gap,md,ms);

            int gb=gapBucket(gap),g32=gradeIndex(grade32),g33=gradeIndex(grade33);

            h32.n++;if(ok32)h32.hit++;
            h32.baseN++;if(ok31)h32.baseHit++;
            h32.gradeN[g32]++;if(ok32)h32.gradeHit[g32]++;
            h32.gapN[gb]++;if(ok32)h32.gapHit[gb]++;
            h32.contextN[ctx]++;if(ok32)h32.contextHit[ctx]++;

            h33.n++;if(ok33)h33.hit++;
            h33.baseN++;if(ok32)h33.baseHit++;
            h33.gradeN[g33]++;if(ok33)h33.gradeHit[g33]++;
            h33.gapN[gb]++;if(ok33)h33.gapHit[gb]++;
            h33.contextN[ctx]++;if(ok33)h33.contextHit[ctx]++;

            q33.addLast(ok33);if(ok33)r33++;
            if(q33.size()>50&&q33.removeFirst())r33--;
            q32.addLast(ok32);if(ok32)r32++;
            if(q32.size()>50&&q32.removeFirst())r32--;
            q31.addLast(ok31);if(ok31)r31++;
            if(q31.size()>50&&q31.removeFirst())r31--;

            // 메타 상태는 반드시 이번 실제결과를 보기 전에 선택한 md 이후에 업데이트한다.
            updateMeta(ms,ctx,baseExc,d32.exclude,md,actual);

            updatePolicy(ps,ctx,baseGrade,STRAT_BASE,ok31);
            if(rv.exclude>0)updatePolicy(ps,ctx,baseGrade,STRAT_CONSENSUS,actual!=rv.exclude);
            updatePolicy(ps,ctx,baseGrade,STRAT_V1,actual!=v1Exc);

            for(int e=0;e<V1EC;e++){
                boolean vok=actual!=argmin(legacyV1Pred(a,t,e));
                v1n[e]++;if(vok)v1hit[e]++;
                v1q[e].addLast(vok);if(vok)v1rh[e]++;
                if(v1q[e].size()>60&&v1q[e].removeFirst())v1rh[e]--;
            }

            for(int e=0;e<ENGINE_COUNT;e++){
                boolean eok=actual!=argmin(pred(a,t,e));
                en[e]++;if(eok)eh[e]++;
                eq[e].addLast(eok);if(eok)erh[e]++;
                if(eq[e].size()>60&&eq[e].removeFirst())erh[e]--;

                cn[ctx][e]++;if(eok)ch[ctx][e]++;
                cq[ctx][e].addLast(eok);if(eok)crh[ctx][e]++;
                if(cq[ctx][e].size()>35&&cq[ctx][e].removeFirst())crh[ctx][e]--;
            }
        }

        h33.rn=q33.size();h33.rhit=r33;
        h32.rn=q32.size();h32.rhit=r32;
        h32.baseRN=q31.size();h32.baseRHit=r31;
        h33.baseRN=q32.size();h33.baseRHit=r32;

        BacktestBundle out=new BacktestBundle();
        out.stats=h33;out.stats32=h32;out.policy=ps;out.metaState=ms;out.metaStats=metaSnapshot(ms);
        return out;
    }

    private static MetaDecision chooseMeta(int ctx,String baseGrade,int v31Exc,int v32Exc,int v32Strategy,PolicyState ps,MetaState ms){
        MetaDecision d=new MetaDecision();
        if(v31Exc==v32Exc){
            d.exclude=v31Exc;d.source=0;d.agreement=true;
            d.evidenceN=ms.agreeN;
            double ar=shrunkRate(ms.agreeHit,ms.agreeN,20);
            d.v31Score=ar;d.v32Score=ar;
            d.mode="V3.1·V3.2 동일값 · 합의";
            return d;
        }

        d.agreement=false;
        int gi=gradeIndex(baseGrade);
        int cn=ms.ctxN[ctx];
        int rn=ms.recent31.size();

        double overall31=shrunkRate(ms.conflict31Hit,ms.conflictN,24);
        double overall32=shrunkRate(ms.conflict32Hit,ms.conflictN,24);
        double context31=shrunkRate(ms.ctx31Hit[ctx],cn,14);
        double context32=shrunkRate(ms.ctx32Hit[ctx],cn,14);
        double recent31=shrunkRate(ms.recent31Hit,rn,12);
        double recent32=shrunkRate(ms.recent32Hit,rn,12);

        double globalGate=Math.min(1.0,ms.conflictN/24.0);
        double contextGate=cn<8?0.0:Math.min(1.0,(cn-8)/12.0);
        double recentGate=Math.min(1.0,rn/15.0);

        double p31=policyRate(ps,ctx,gi,STRAT_BASE);
        double p32=policyRate(ps,ctx,gi,v32Strategy);
        int pn31=policyN(ps,ctx,gi,STRAT_BASE);
        int pn32=policyN(ps,ctx,gi,v32Strategy);
        double policyGate=Math.min(1.0,Math.min(pn31,pn32)/14.0);

        d.v31Score=.75
                +.42*globalGate*(overall31-.75)
                +.33*contextGate*(context31-.75)
                +.17*recentGate*(recent31-.75)
                +.08*policyGate*(p31-.75);
        d.v32Score=.75
                +.42*globalGate*(overall32-.75)
                +.33*contextGate*(context32-.75)
                +.17*recentGate*(recent32-.75)
                +.08*policyGate*(p32-.75);
        d.evidenceN=Math.max(cn,ms.conflictN);

        boolean thin=ms.conflictN<8&&cn<6;
        if(thin){
            if(pn31>=8&&pn32>=8&&p31>p32+.018){
                d.exclude=v31Exc;d.source=31;d.mode="충돌표본부족 · 동일상황 V3.1 우세";
            }else{
                d.exclude=v32Exc;d.source=32;d.mode="충돌표본부족 · 검증된 V3.2 유지";
            }
            return d;
        }

        double diff=d.v31Score-d.v32Score;
        if(diff>.006){
            d.exclude=v31Exc;d.source=31;d.mode="충돌 메타판정 · V3.1 우세";
        }else if(diff<-.006){
            d.exclude=v32Exc;d.source=32;d.mode="충돌 메타판정 · V3.2 우세";
        }else if(pn31>=8&&pn32>=8&&p31>p32+.012){
            d.exclude=v31Exc;d.source=31;d.mode="메타근소차 · 동일상황 V3.1 우세";
        }else{
            d.exclude=v32Exc;d.source=32;d.mode="메타근소차 · V3.2 유지";
        }
        return d;
    }

    private static void updateMeta(MetaState ms,int ctx,int v31Exc,int v32Exc,MetaDecision md,int actual){
        boolean ok31=actual!=v31Exc,ok32=actual!=v32Exc,okFinal=actual!=md.exclude;
        if(v31Exc==v32Exc){
            ms.agreeN++;if(ok31)ms.agreeHit++;
            return;
        }

        ms.conflictN++;if(ok31)ms.conflict31Hit++;if(ok32)ms.conflict32Hit++;if(okFinal)ms.conflictFinalHit++;
        ms.ctxN[ctx]++;if(ok31)ms.ctx31Hit[ctx]++;if(ok32)ms.ctx32Hit[ctx]++;

        ms.recent31.addLast(ok31);if(ok31)ms.recent31Hit++;
        if(ms.recent31.size()>30&&ms.recent31.removeFirst())ms.recent31Hit--;
        ms.recent32.addLast(ok32);if(ok32)ms.recent32Hit++;
        if(ms.recent32.size()>30&&ms.recent32.removeFirst())ms.recent32Hit--;

        if(md.source==31){ms.choose31N++;if(okFinal)ms.choose31Hit++;}
        else if(md.source==32){ms.choose32N++;if(okFinal)ms.choose32Hit++;}
    }

    private static MetaStats metaSnapshot(MetaState ms){
        MetaStats m=new MetaStats();
        m.agreeN=ms.agreeN;m.agreeHit=ms.agreeHit;
        m.conflictN=ms.conflictN;m.conflictV31Hit=ms.conflict31Hit;m.conflictV32Hit=ms.conflict32Hit;m.conflictFinalHit=ms.conflictFinalHit;
        m.choose31N=ms.choose31N;m.choose31Hit=ms.choose31Hit;m.choose32N=ms.choose32N;m.choose32Hit=ms.choose32Hit;
        m.recentN=ms.recent31.size();m.recent31Hit=ms.recent31Hit;m.recent32Hit=ms.recent32Hit;
        for(int i=0;i<CONTEXT_COUNT;i++){
            m.contextN[i]=ms.ctxN[i];m.context31Hit[i]=ms.ctx31Hit[i];m.context32Hit[i]=ms.ctx32Hit[i];
        }
        return m;
    }

    private static double shrunkRate(int hit,int n,double k){
        return n==0?.75:shrink((double)hit/n,n,k,.75);
    }

    private static String metaGrade(String v32Grade,double gap,MetaDecision md,MetaState ms){
        if(md.agreement){
            double ar=shrunkRate(ms.agreeHit,ms.agreeN,20);
            if(ms.agreeN>=12&&ar>=.80&&gap>=7)return "강";
            if(ms.agreeN>=12&&ar<.745)return "약";
            return "강".equals(v32Grade)?"보통":v32Grade;
        }
        double chosen=md.source==31?md.v31Score:md.v32Score;
        double diff=Math.abs(md.v31Score-md.v32Score);
        if(md.evidenceN>=10){
            if(chosen>=.795&&diff>=.012)return "강";
            if(chosen>=.765)return "보통";
            return "약";
        }
        return "강".equals(v32Grade)?"보통":v32Grade;
    }

    private static RescueVote rescueVote(List<Result>a,int end){
        int[] vote=new int[5];
        for(int e:RESCUE_ENGINES)vote[argmin(pred(a,end,e))]++;
        int best=1;
        for(int k=2;k<=4;k++)if(vote[k]>vote[best])best=k;
        RescueVote r=new RescueVote();r.votes=vote[best];r.exclude=r.votes>=3?best:0;return r;
    }

    private static Decision chooseDecision(int ctx,String baseGrade,int baseExc,RescueVote rv,int v1Exc,PolicyState ps){
        Decision d=new Decision();d.exclude=baseExc;d.strategy=STRAT_BASE;d.mode="V3.1 기본";
        int gi=gradeIndex(baseGrade);
        d.baseRate=policyRate(ps,ctx,gi,STRAT_BASE);d.v1Rate=policyRate(ps,ctx,gi,STRAT_V1);
        d.baseN=policyN(ps,ctx,gi,STRAT_BASE);d.v1N=policyN(ps,ctx,gi,STRAT_V1);

        boolean rescue=(ctx==2)||"약".equals(baseGrade);
        if(!rescue)return d;

        // V3.2 내부 안전장치: 재투표는 '후보'를 만들 뿐, 과거 동일상황 순차검증이
        // 기본 V3.1보다 실제로 낫다는 증거가 있을 때만 제외값을 바꾼다.
        if(rv!=null&&rv.exclude>0){
            int cn=policyN(ps,ctx,gi,STRAT_CONSENSUS);
            double cr=policyRate(ps,ctx,gi,STRAT_CONSENSUS);
            if(rv.exclude==baseExc){
                d.mode="5엔진 "+rv.votes+"/5 · V3.1과 동일";
                return d;
            }
            boolean proven=cn>=8&&cr>d.baseRate+.010;
            boolean weakRescue="약".equals(baseGrade)&&d.baseN>=8&&d.baseRate<.70&&cn>=6&&cr>=.74;
            if(proven||weakRescue){
                d.exclude=rv.exclude;d.strategy=STRAT_CONSENSUS;
                d.mode="5엔진 "+rv.votes+"/5 · 검증우세 재판정";
                return d;
            }
            d.mode="5엔진 "+rv.votes+"/5 · 검증미달 V3.1 유지";
        }

        // 합의가 없거나 재투표의 검증우위가 부족하면 V1과 V3.1을 같은 상황에서 비교.
        // V1 역시 최소 표본 + 1.2%p 이상의 보정 우위가 있을 때만 전환한다.
        if(d.v1N>=8&&d.v1Rate>d.baseRate+.012){
            d.exclude=v1Exc;d.strategy=STRAT_V1;d.mode="동일상황 V1 검증우세";
        }else if(rv==null||rv.exclude==0){
            d.mode="합의분산 · V3.1 유지";
        }
        return d;
    }

    private static void updatePolicy(PolicyState ps,int ctx,String grade,int strategy,boolean ok){
        int gi=gradeIndex(grade);
        ps.n[ctx][gi][strategy]++;if(ok)ps.hit[ctx][gi][strategy]++;
        ps.ctxN[ctx][strategy]++;if(ok)ps.ctxHit[ctx][strategy]++;
    }

    private static int policyN(PolicyState ps,int ctx,int gi,int strategy){
        int n=ps.n[ctx][gi][strategy];
        return n>=6?n:ps.ctxN[ctx][strategy];
    }

    private static double policyRate(PolicyState ps,int ctx,int gi,int strategy){
        int n=ps.n[ctx][gi][strategy],h=ps.hit[ctx][gi][strategy];
        if(n>=6)return shrink((double)h/n,n,12,.75);
        n=ps.ctxN[ctx][strategy];h=ps.ctxHit[ctx][strategy];
        if(n==0)return .75;
        return shrink((double)h/n,n,20,.75);
    }

    private static String calibratedGrade(String base,double gap,int ctx,int strategy,PolicyState ps){
        int gi=gradeIndex(base),n=policyN(ps,ctx,gi,strategy);
        double r=policyRate(ps,ctx,gi,strategy);
        if(n>=10){
            if(r>=.805&&gap>=9)return "강";
            if(r>=.765)return "보통";
            return "약";
        }
        if(strategy!=STRAT_BASE&&"강".equals(base))return "보통";
        return base;
    }

    private static double[] legacyV1Forecast(List<Result>a,int end,int[] n,int[] hit,ArrayDeque<Boolean>[]q,int[]rh){
        final int EC=8;double[] ens=new double[5];double ws=0;
        for(int e=0;e<EC;e++){
            double[] p=legacyV1Pred(a,end,e);
            double all=n[e]>0?(double)hit[e]/n[e]:.75;
            double rec=q[e].isEmpty()?all:(double)rh[e]/q[e].size();
            double s=shrink(all,n[e],80,.75),r=shrink(rec,q[e].size(),40,.75);
            double w=clamp(1+8*(s-.75)+5*(r-.75),.25,2.25);
            ws+=w;for(int k=1;k<=4;k++)ens[k]+=p[k]*w;
        }
        for(int k=1;k<=4;k++)ens[k]/=Math.max(.0001,ws);
        return norm(ens);
    }

    private static double[] legacyV1Current(List<Result>a,int end){
        final int EC=8;int[] n=new int[EC],hit=new int[EC],rh=new int[EC];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] q=new ArrayDeque[EC];
        for(int e=0;e<EC;e++)q[e]=new ArrayDeque<>();
        int start=Math.max(18,end-BT_LIMIT);
        for(int t=start;t<end;t++){
            int actual=a.get(t).combo;
            for(int e=0;e<EC;e++){
                boolean ok=actual!=argmin(legacyV1Pred(a,t,e));
                n[e]++;if(ok)hit[e]++;
                q[e].addLast(ok);if(ok)rh[e]++;
                if(q[e].size()>60&&q[e].removeFirst())rh[e]--;
            }
        }
        return legacyV1Forecast(a,end,n,hit,q,rh);
    }

    private static CompareStats compareTest(List<Result>a,HedgeStats v32,HedgeStats v33){
        CompareStats c=new CompareStats();
        BacktestRate v1=legacyV1Backtest(a);
        BacktestRate v2=legacyV2Backtest(a);
        c.v1N=v1.n;c.v1Hit=v1.hit;
        c.v2N=v2.n;c.v2Hit=v2.hit;
        c.v3N=v32.baseN;c.v3Hit=v32.baseHit;
        c.v32N=v32.n;c.v32Hit=v32.hit;
        c.v33N=v33.n;c.v33Hit=v33.hit;
        return c;
    }

    private static final class BacktestRate { int n,hit; }

    private static BacktestRate legacyV1Backtest(List<Result>a){
        BacktestRate out=new BacktestRate();
        final int EC=8;
        int end=a.size(),start=Math.max(24,end-BT_LIMIT);
        int[] n=new int[EC],hit=new int[EC],rh=new int[EC];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] q=new ArrayDeque[EC];
        for(int e=0;e<EC;e++)q[e]=new ArrayDeque<>();

        for(int t=start;t<end;t++){
            double[] ens=new double[5]; double ws=0;
            for(int e=0;e<EC;e++){
                double[] p=legacyV1Pred(a,t,e);
                double all=n[e]>0?(double)hit[e]/n[e]:.75;
                double rec=q[e].isEmpty()?all:(double)rh[e]/q[e].size();
                double s=.75+(all-.75)*(n[e]/(n[e]+80.0));
                double r=.75+(rec-.75)*(q[e].size()/(q[e].size()+40.0));
                double w=clamp(1+8*(s-.75)+5*(r-.75),.25,2.25);
                ws+=w;for(int k=1;k<=4;k++)ens[k]+=p[k]*w;
            }
            for(int k=1;k<=4;k++)ens[k]/=Math.max(.0001,ws);
            norm(ens);
            int actual=a.get(t).combo;
            boolean ok=actual!=argmin(ens);
            out.n++;if(ok)out.hit++;

            for(int e=0;e<EC;e++){
                boolean eok=actual!=argmin(legacyV1Pred(a,t,e));
                n[e]++;if(eok)hit[e]++;
                q[e].addLast(eok);if(eok)rh[e]++;
                if(q[e].size()>60&&q[e].removeFirst())rh[e]--;
            }
        }
        return out;
    }

    private static double[] legacyV1Pred(List<Result>a,int end,int id){
        switch(id){
            case 0:return freq(a,end,8,1.15);
            case 1:return freq(a,end,15,1.0);
            case 2:return freq(a,end,30,.65);
            case 3:return markov1(a,end);
            case 4:return markov2(a,end);
            case 5:return binary(a,end);
            case 6:return regime(a,end);
            case 7:return streak(a,end);
            default:return uni();
        }
    }

    private static BacktestRate legacyV2Backtest(List<Result>a){
        BacktestRate out=new BacktestRate();
        int end=a.size(),start=Math.max(24,end-BT_LIMIT);
        int[] en=new int[ENGINE_COUNT],eh=new int[ENGINE_COUNT],erh=new int[ENGINE_COUNT];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] eq=new ArrayDeque[ENGINE_COUNT];
        for(int e=0;e<ENGINE_COUNT;e++)eq[e]=new ArrayDeque<>();

        int[] fn=new int[3],fh=new int[3],frh=new int[3];
        @SuppressWarnings("unchecked") ArrayDeque<Boolean>[] fq=new ArrayDeque[3];
        for(int f=0;f<3;f++)fq[f]=new ArrayDeque<>();

        for(int t=start;t<end;t++){
            double[][] pp=new double[ENGINE_COUNT][];
            double[] ew=new double[ENGINE_COUNT];
            for(int e=0;e<ENGINE_COUNT;e++){
                pp[e]=pred(a,t,e);
                Perf pe=perfFrom(en[e],eh[e],eq[e].size(),erh[e]);
                ew[e]=legacyV2Weight(pe);
            }

            double[][] fam=new double[3][5]; double[] fsw=new double[3];
            for(int e=0;e<ENGINE_COUNT;e++){
                int f=(e<=2?0:e<=6?1:2);
                fsw[f]+=ew[e];
                for(int k=1;k<=4;k++)fam[f][k]+=pp[e][k]*ew[e];
            }
            for(int f=0;f<3;f++){
                for(int k=1;k<=4;k++)fam[f][k]/=Math.max(.0001,fsw[f]);
                norm(fam[f]);
            }

            double[] ens=new double[5];double ws=0;
            for(int f=0;f<3;f++){
                Perf pf=perfFrom(fn[f],fh[f],fq[f].size(),frh[f]);
                double w=legacyV2Weight(pf);
                ws+=w;for(int k=1;k<=4;k++)ens[k]+=fam[f][k]*w;
            }
            for(int k=1;k<=4;k++)ens[k]/=Math.max(.0001,ws);
            norm(ens);

            int actual=a.get(t).combo;
            boolean ok=actual!=argmin(ens);
            out.n++;if(ok)out.hit++;

            for(int e=0;e<ENGINE_COUNT;e++){
                boolean eok=actual!=argmin(pp[e]);
                en[e]++;if(eok)eh[e]++;
                eq[e].addLast(eok);if(eok)erh[e]++;
                if(eq[e].size()>60&&eq[e].removeFirst())erh[e]--;
            }
            for(int f=0;f<3;f++){
                boolean fok=actual!=argmin(fam[f]);
                fn[f]++;if(fok)fh[f]++;
                fq[f].addLast(fok);if(fok)frh[f]++;
                if(fq[f].size()>60&&fq[f].removeFirst())frh[f]--;
            }
        }
        return out;
    }

    private static double legacyV2Weight(Perf p){
        double all=shrink(p.rate(),p.n,90,.75),rec=shrink(p.recentRate(),p.rn,35,.75);
        double pos=6*Math.max(0,all-.75)+4*Math.max(0,rec-.75);
        double neg=12*Math.max(0,.75-all)+9*Math.max(0,.75-rec);
        return clamp(1+pos-neg,.15,2.35);
    }

    private static Perf perfFrom(int n,int hit,int rn,int rhit){
        Perf p=new Perf();p.n=n;p.hit=hit;p.rn=rn;p.rhit=rhit;return p;
    }

    private static int contextId(List<Result>a,int end){
        if(end<8)return 0;
        int last=a.get(end-1).combo,st=1;
        for(int i=end-2;i>=0&&a.get(i).combo==last&&st<5;i--)st++;
        if(st>=2)return 2;

        double[] s=freq(a,end,8,1.0),l=freq(a,end,40,.35);
        double tv=0;
        for(int k=1;k<=4;k++)tv+=Math.abs(s[k]-l[k]);
        tv*=.5;
        if(tv>=.24)return 1;

        int[] cnt=new int[5];
        int from=Math.max(0,end-12),n=end-from,max=0;
        for(int i=from;i<end;i++){cnt[a.get(i).combo]++;max=Math.max(max,cnt[a.get(i).combo]);}
        if(n>=8&&(double)max/n>=.42)return 3;
        return 0;
    }

    private static int gapBucket(double gap){
        if(gap<5)return 0;
        if(gap<10)return 1;
        if(gap<15)return 2;
        return 3;
    }

    private static double scoreGap(double[]p){
        int first=1,second=2;
        if(p[second]<p[first]){int z=first;first=second;second=z;}
        for(int k=3;k<=4;k++){
            if(p[k]<p[first]){second=first;first=k;}
            else if(p[k]<p[second])second=k;
        }
        return clamp((p[second]-p[first])*400.0,0,100);
    }

    private static double excludeScore(double occurrence){
        return clamp(50.0+(.25-occurrence)*400.0,0,100);
    }

    private static String classify(double gap,double recent,int n){
        if(gap>=15&&(n<20||recent>=.77))return "강";
        if(gap>=7&&(n<20||recent>=.745))return "보통";
        return "약";
    }

    private static int gradeIndex(String g){return "강".equals(g)?2:"보통".equals(g)?1:0;}

    private static double[] pred(List<Result>a,int end,int id){
        switch(id){
            case 0:return freq(a,end,8,1.15);
            case 1:return freq(a,end,15,1.0);
            case 2:return freq(a,end,30,.65);
            case 3:return markov1(a,end);
            case 4:return markov2(a,end);
            case 5:return streak(a,end);
            case 6:return similarInfo(a,end).p;
            case 7:return binary(a,end);
            case 8:return regime(a,end);
            default:return uni();
        }
    }

    private static double[] freq(List<Result>a,int end,int win,double pow){
        double[]c=prior();double tot=6;
        int s=Math.max(0,end-win),pos=1;
        for(int i=s;i<end;i++){
            double w=Math.pow(pos++,pow);
            c[a.get(i).combo]+=w;tot+=w;
        }
        for(int k=1;k<=4;k++)c[k]/=tot;
        return norm(c);
    }

    private static double[] markov1(List<Result>a,int end){
        if(end<2)return freq(a,end,15,1);
        int last=a.get(end-1).combo;
        double[]c=prior();double tot=6;
        for(int i=Math.max(1,end-1200);i<end;i++)if(a.get(i-1).combo==last){c[a.get(i).combo]++;tot++;}
        for(int k=1;k<=4;k++)c[k]/=tot;
        return norm(c);
    }

    private static double[] markov2(List<Result>a,int end){
        if(end<3)return markov1(a,end);
        int x=a.get(end-2).combo,y=a.get(end-1).combo,m=0;
        double[]c=prior();double tot=6;
        for(int i=Math.max(2,end-1800);i<end;i++){
            if(a.get(i-2).combo==x&&a.get(i-1).combo==y){
                c[a.get(i).combo]++;tot++;m++;
            }
        }
        for(int k=1;k<=4;k++)c[k]/=tot;
        c=norm(c);
        return m<6?mix(markov1(a,end),c,.72):c;
    }

    private static double[] streak(List<Result>a,int end){
        if(end<3)return freq(a,end,12,1);
        int last=a.get(end-1).combo,st=1;
        for(int i=end-2;i>=0&&a.get(i).combo==last&&st<5;i--)st++;
        double[]c=prior();double tot=6;int m=0;
        for(int i=Math.max(2,end-1800);i<end;i++){
            int prev=a.get(i-1).combo;
            if(prev!=last)continue;
            int ss=1;
            for(int j=i-2;j>=0&&a.get(j).combo==prev&&ss<5;j--)ss++;
            if(ss==st){c[a.get(i).combo]++;tot++;m++;}
        }
        for(int k=1;k<=4;k++)c[k]/=tot;
        c=norm(c);
        return m<6?mix(freq(a,end,12,1),c,.76):c;
    }

    private static SimilarInfo similarInfo(List<Result>a,int end){
        SimilarInfo out=new SimilarInfo();
        if(end<5){
            out.p=markov2(a,end);out.matches=0;return out;
        }
        int len=Math.min(5,end);
        double[]c=prior();double tot=6;int matches=0;
        for(int i=Math.max(len,end-2200);i<end;i++){
            int dist=0;
            for(int j=1;j<=len;j++)if(a.get(end-j).combo!=a.get(i-j).combo)dist+=j<=2?2:1;
            if(dist<=3){
                double w=dist==0?5.0:dist==1?3.0:dist==2?1.8:1.0;
                c[a.get(i).combo]+=w;tot+=w;matches++;
            }
        }
        for(int k=1;k<=4;k++)c[k]/=tot;
        out.p=norm(c);out.matches=matches;
        return out;
    }

    private static double similarBlendWeight(int matches){
        if(matches<8)return 0;
        return clamp(.10+Math.min(80,matches)*.001,.10,.18);
    }

    private static double[] binary(List<Result>a,int end){
        double pr=bitProb(a,end,true),pf=bitProb(a,end,false),pl=1-pr,p3=1-pf;
        double[]p=new double[5];
        p[1]=pl*p3;p[2]=pl*pf;p[3]=pr*p3;p[4]=pr*pf;
        return norm(p);
    }

    private static double bitProb(List<Result>a,int end,boolean right){
        int s=Math.max(0,end-24),pos=1;
        double ones=1.5,tot=3;
        for(int i=s;i<end;i++){
            int b=right?right(a.get(i).combo):four(a.get(i).combo);
            double w=pos++;
            if(b==1)ones+=w;tot+=w;
        }
        double f=ones/tot;
        if(end<2)return clamp(f,.08,.92);
        int last=right?right(a.get(end-1).combo):four(a.get(end-1).combo);
        double to=1.5,tt=3;
        for(int i=Math.max(1,end-1000);i<end;i++){
            int prev=right?right(a.get(i-1).combo):four(a.get(i-1).combo);
            if(prev==last){
                int cur=right?right(a.get(i).combo):four(a.get(i).combo);
                if(cur==1)to++;tt++;
            }
        }
        return clamp(.6*f+.4*(to/tt),.08,.92);
    }

    private static int right(int c){return c==3||c==4?1:0;}
    private static int four(int c){return c==2||c==4?1:0;}

    private static double[] regime(List<Result>a,int end){
        double[]s=freq(a,end,12,1),m=freq(a,end,30,.65),l=freq(a,end,70,.25);
        double tv=0;
        for(int k=1;k<=4;k++)tv+=Math.abs(s[k]-l[k]);
        tv*=.5;
        double alpha=clamp(.42+1.9*tv,.42,.86);
        double[]base=mix(m,l,.68);
        return mix(s,base,alpha);
    }

    private static double[] prior(){
        double[]p=new double[5];for(int k=1;k<=4;k++)p[k]=1.5;return p;
    }
    private static double[]uni(){
        double[]p=new double[5];for(int k=1;k<=4;k++)p[k]=.25;return p;
    }
    private static double[]mix(double[]a,double[]b,double wa){
        double[]p=new double[5];
        for(int k=1;k<=4;k++)p[k]=a[k]*wa+b[k]*(1-wa);
        return norm(p);
    }
    private static double[]norm(double[]p){
        double s=0;
        for(int k=1;k<=4;k++){
            if(Double.isNaN(p[k])||Double.isInfinite(p[k])||p[k]<0)p[k]=0;
            s+=p[k];
        }
        if(s<=0)return uni();
        for(int k=1;k<=4;k++)p[k]/=s;
        return p;
    }
    private static int argmin(double[]p){
        int b=1;for(int k=2;k<=4;k++)if(p[k]<p[b])b=k;return b;
    }
    private static double shrink(double rate,int n,double k,double base){
        return base+(rate-base)*(n/(n+k));
    }
    private static double clamp(double x,double lo,double hi){
        return Math.max(lo,Math.min(hi,x));
    }

    public static String tripleFor(int c){
        switch(c){
            case 1:return "우 + 4줄 + 홀";
            case 2:return "우 + 3줄 + 짝";
            case 3:return "좌 + 4줄 + 짝";
            case 4:return "좌 + 3줄 + 홀";
            default:return "-";
        }
    }

    public static long nextIdx(Result r){
        try{
            if(r.round<480)return Long.parseLong(r.date.substring(2,8)+String.format(Locale.US,"%04d",r.round+1));
            SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);
            Calendar c=Calendar.getInstance();c.setTime(f.parse(r.date));c.add(Calendar.DAY_OF_MONTH,1);
            String d=f.format(c.getTime());
            return Long.parseLong(d.substring(2,8)+"0001");
        }catch(Exception e){return r.idx+1;}
    }

    public static long millisToNextDraw(){
        long interval=180000L,now=System.currentTimeMillis(),mod=Math.floorMod(now,interval),left=interval-mod;
        return left==0?interval:left;
    }

    public static String countdownText(){
        long s=(millisToNextDraw()+999)/1000;
        return String.format(Locale.KOREA,"%02d:%02d",s/60,s%60);
    }

    public static double successProfit(int stake,double odds){return stake*(2*odds-3);}
    public static double breakEven(double odds){return 3/(2*odds);}
    public static String pct(double v){return String.format(Locale.KOREA,"%.1f%%",v*100);}
    public static String money(double v){return String.format(Locale.KOREA,"%,.0f원",v);}
    public static String signed(double v){return (v>=0?"+":"")+money(v);}

    public static String liveRate(Context c){
        SharedPreferences sp=prefs(c);
        int n=sp.getInt(K_LIVE_TOTAL,0),h=sp.getInt(K_LIVE_SUCCESS,0);
        return n==0?"-":h+"/"+n+" ("+pct((double)h/n)+")";
    }

    public static JSONObject backup(Context c) throws Exception {
        SharedPreferences sp=prefs(c);
        JSONObject root=new JSONObject();
        root.put("format","BubbleTripleHedgeV33Backup");
        root.put("history",new JSONArray(sp.getString(K_HISTORY,"[]")));
        root.put("records",new JSONArray(sp.getString(K_RECORDS,"[]")));
        JSONObject s=new JSONObject();
        s.put(K_PENDING_IDX,sp.getLong(K_PENDING_IDX,-1));
        s.put(K_PENDING_EXCLUDE,sp.getInt(K_PENDING_EXCLUDE,0));
        s.put(K_PENDING_STAKE,sp.getInt(K_PENDING_STAKE,5000));
        s.put(K_PENDING_ODDS,sp.getFloat(K_PENDING_ODDS,1.95f));
        s.put(K_PENDING_GRADE,sp.getString(K_PENDING_GRADE,"약"));
        s.put(K_LIVE_TOTAL,sp.getInt(K_LIVE_TOTAL,0));
        s.put(K_LIVE_SUCCESS,sp.getInt(K_LIVE_SUCCESS,0));
        s.put(K_LIVE_PROFIT,sp.getLong(K_LIVE_PROFIT,Double.doubleToLongBits(0)));
        s.put(K_BASE_STAKE,sp.getInt(K_BASE_STAKE,5000));
        s.put(K_ODDS,sp.getFloat(K_ODDS,1.95f));
        s.put(K_AUTO,sp.getBoolean(K_AUTO,true));
        root.put("state",s);
        return root;
    }

    public static void restore(Context c,JSONObject root) throws Exception {
        String format=root.optString("format","");
        boolean full="BubbleTripleHedgeV33Backup".equals(format);
        boolean legacyV3="BubbleTripleHedgeV3Backup".equals(format);
        boolean legacy="BubbleTripleHedgeV32Backup".equals(format)||
                "BubbleTripleHedgeV2Backup".equals(format)||
                "BubbleTripleHedgeBackup".equals(format);
        if(legacyV3)legacy=true;
        if(!full&&!legacy)throw new Exception("백업 형식이 다릅니다.");

        SharedPreferences.Editor ed=prefs(c).edit();
        if(root.has("history"))ed.putString(K_HISTORY,root.getJSONArray("history").toString());

        JSONObject s=root.optJSONObject("state");
        if(full){
            if(root.has("records"))ed.putString(K_RECORDS,root.getJSONArray("records").toString());
            if(s!=null){
                if(s.has(K_PENDING_IDX))ed.putLong(K_PENDING_IDX,s.optLong(K_PENDING_IDX,-1));
                if(s.has(K_PENDING_EXCLUDE))ed.putInt(K_PENDING_EXCLUDE,s.optInt(K_PENDING_EXCLUDE,0));
                if(s.has(K_PENDING_STAKE))ed.putInt(K_PENDING_STAKE,s.optInt(K_PENDING_STAKE,5000));
                if(s.has(K_PENDING_ODDS))ed.putFloat(K_PENDING_ODDS,(float)s.optDouble(K_PENDING_ODDS,1.95));
                if(s.has(K_PENDING_GRADE))ed.putString(K_PENDING_GRADE,s.optString(K_PENDING_GRADE,"약"));
                if(s.has(K_LIVE_TOTAL))ed.putInt(K_LIVE_TOTAL,s.optInt(K_LIVE_TOTAL,0));
                if(s.has(K_LIVE_SUCCESS))ed.putInt(K_LIVE_SUCCESS,s.optInt(K_LIVE_SUCCESS,0));
                if(s.has(K_LIVE_PROFIT))ed.putLong(K_LIVE_PROFIT,s.optLong(K_LIVE_PROFIT,Double.doubleToLongBits(0)));
                if(s.has(K_BASE_STAKE))ed.putInt(K_BASE_STAKE,s.optInt(K_BASE_STAKE,5000));
                if(s.has(K_ODDS))ed.putFloat(K_ODDS,(float)s.optDouble(K_ODDS,1.95));
                if(s.has(K_AUTO))ed.putBoolean(K_AUTO,s.optBoolean(K_AUTO,true));
            }
        }else if(s!=null){
            // V1/V2/V3.1/V3.2 import: history + stake/odds only. 실전 성적은 V3.3 Final 검증을 위해 분리한다.
            if(s.has("base_stake_v32"))ed.putInt(K_BASE_STAKE,Math.max(5000,s.optInt("base_stake_v32",5000)));
            else if(s.has("base_stake_v3"))ed.putInt(K_BASE_STAKE,Math.max(5000,s.optInt("base_stake_v3",5000)));
            else if(s.has("base_stake_v2"))ed.putInt(K_BASE_STAKE,Math.max(5000,s.optInt("base_stake_v2",5000)));
            else if(s.has("pending_stake"))ed.putInt(K_BASE_STAKE,Math.max(5000,s.optInt("pending_stake",5000)));
            if(s.has("odds_v32"))ed.putFloat(K_ODDS,(float)s.optDouble("odds_v32",1.95));
            else if(s.has("odds_v3"))ed.putFloat(K_ODDS,(float)s.optDouble("odds_v3",1.95));
            else if(s.has("odds_v2"))ed.putFloat(K_ODDS,(float)s.optDouble("odds_v2",1.95));
            else if(s.has("pending_odds"))ed.putFloat(K_ODDS,(float)s.optDouble("pending_odds",1.95));
        }
        ed.apply();
    }
}

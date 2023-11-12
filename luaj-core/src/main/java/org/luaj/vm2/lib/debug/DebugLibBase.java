package org.luaj.vm2.lib.debug;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;

public class DebugLibBase extends TwoArgFunction {

    protected final static LuaString WORD_CALL = valueOf("call");
    protected final static LuaString WORD_LINE = valueOf("line");
    protected final static LuaString WORD_COUNT = valueOf("count");
    protected final static LuaString WORD_RETURN = valueOf("return");
    protected final static LuaString WORD_QMARK = valueOf("?");

    private Globals globals;

    private boolean trace;

    public void setTrace(final boolean trace) {
        this.trace = trace;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        globals = env.checkglobals();
        globals.debuglib = this;
        return env;
    }

    public void onCall(LuaFunction function) {
        LuaThread.State s = globals.running.state;
        if (s.inhook)
            return;
        callstack().onCall(function);
        if (s.hookcall)
            callHook(s, WORD_CALL, NIL);
    }

    public void onCall(LuaClosure closure, Varargs varargs, LuaValue[] stack) {
        LuaThread.State s = globals.running.state;
        if (s.inhook)
            return;
        callstack().onCall(closure, varargs, stack);
        if (s.hookcall)
            callHook(s, WORD_CALL, NIL);
    }

    public void onInstruction(int pc, Varargs varargs, int top) {
        LuaThread.State s = globals.running.state;
        if (s.inhook)
            return;
        callstack().onInstruction(pc, varargs, top);
        if (s.hookfunc == null)
            return;
        if (s.hookcount > 0)
            if (++s.bytecodes%s.hookcount == 0)
                callHook(s, WORD_COUNT, NIL);
        if (s.hookline) {
            int newline = callstack().currentline();
            if (newline != s.lastline) {
                s.lastline = newline;
                callHook(s, WORD_LINE, valueOf(newline));
            }
        }
    }

    public void onReturn() {
        LuaThread.State s = globals.running.state;
        if (s.inhook)
            return;
        callstack().onReturn();
        if (s.hookrtrn)
            callHook(s, WORD_RETURN, NIL);
    }

    private void callHook(LuaThread.State s, LuaValue type, LuaValue arg) {
        if (s.inhook || s.hookfunc == null)
            return;
        s.inhook = true;
        try {
            s.hookfunc.call(type, arg);
        } catch (LuaError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LuaError(e);
        } finally {
            s.inhook = false;
        }
    }

    protected CallStack callstack() {
        return callstack(globals.running);
    }

    protected CallStack callstack(LuaThread t) {
        if (t.callstack == null)
            t.callstack = new CallStack();
        return (CallStack) t.callstack;
    }

    public String traceback(int level) {
        return callstack().traceback(level);
    }

    public CallFrame getCallFrame(int level) {
        return callstack().getCallFrame(level);
    }

    public static class DebugInfo {
        public String    name;            /* (n) */
        public String    namewhat;        /* (n) 'global', 'local', 'field', 'method' */
        public String    what;            /* (S) 'Lua', 'C', 'main', 'tail' */
        public String    source;          /* (S) */
        public int       currentline;     /* (l) */
        public int       linedefined;     /* (S) */
        public int       lastlinedefined; /* (S) */
        public short     nups;            /* (u) number of upvalues */
        public short     nparams;         /* (u) number of parameters */
        public boolean   isvararg;        /* (u) */
        public boolean   istailcall;      /* (t) */
        public String    short_src;       /* (S) */
        public CallFrame cf;              /* active function */

        public void funcinfo(LuaFunction f) {
            if (f.isclosure()) {
                Prototype p = f.checkclosure().p;
                this.source = p.source != null? p.source.tojstring(): "=?";
                this.linedefined = p.linedefined;
                this.lastlinedefined = p.lastlinedefined;
                this.what = this.linedefined == 0? "main": "Lua";
                this.short_src = p.shortsource();
            } else {
                this.source = "=[Java]";
                this.linedefined = -1;
                this.lastlinedefined = -1;
                this.what = "Java";
                this.short_src = f.name();
            }
        }
    }

    public class CallStack {
        private final static CallFrame[] EMPTY = {};
        CallFrame[]              frame = EMPTY;
        int                      calls = 0;

        public CallStack() {}

        public synchronized int currentline() {
            return calls > 0? frame[calls-1].currentline(): -1;
        }

        private synchronized CallFrame pushcall() {
            if (calls >= frame.length) {
                int n = Math.max(4, frame.length*3/2);
                CallFrame[] f = new CallFrame[n];
                System.arraycopy(frame, 0, f, 0, frame.length);
                for (int i = frame.length; i < n; ++i)
                    f[i] = new CallFrame();
                frame = f;
                for (int i = 1; i < n; ++i)
                    f[i].previous = f[i-1];
            }
            return frame[calls++];
        }

        public final synchronized void onCall(LuaFunction function) {
            pushcall().set(function);
        }

        public final synchronized void onCall(LuaClosure function, Varargs varargs, LuaValue[] stack) {
            pushcall().set(function, varargs, stack);
        }

        public final synchronized void onReturn() {
            if (calls > 0)
                frame[--calls].reset();
        }

        public final synchronized void onInstruction(int pc, Varargs v, int top) {
            if (calls > 0)
                frame[calls-1].instr(pc, v, top);
        }

        /**
         * Get the traceback starting at a specific level.
         *
         * @param level
         * @return String containing the traceback.
         */
        public synchronized String traceback(int level) {
            StringBuilder sb = new StringBuilder();
            sb.append("stack traceback:");
            for (CallFrame c; (c = getCallFrame(level++)) != null;) {
                sb.append("\n\t");
                sb.append(c.shortsource());
                sb.append(':');
                if (c.currentline() > 0)
                    sb.append(c.currentline()).append(":");
                sb.append(" in ");
                DebugInfo ar = auxgetinfo("n", c.f, c);
                if (c.linedefined() == 0)
                    sb.append("main chunk");
                else if (ar.name != null) {
                    sb.append("function '");
                    sb.append(ar.name);
                    sb.append('\'');
                } else {
                    sb.append("function <");
                    sb.append(c.shortsource());
                    sb.append(':');
                    sb.append(c.linedefined());
                    sb.append('>');
                }
            }
            sb.append("\n\t[Java]: in ?");
            return sb.toString();
        }

        public synchronized CallFrame getCallFrame(int level) {
            if (level < 1 || level > calls)
                return null;
            return frame[calls-level];
        }

        public synchronized CallFrame findCallFrame(LuaValue func) {
            for (int i = 1; i <= calls; ++i)
                if (frame[calls-i].f == func)
                    return frame[i];
            return null;
        }

        public synchronized DebugInfo auxgetinfo(String what, LuaFunction f, CallFrame ci) {
            DebugInfo ar = new DebugInfo();
            for (int i = 0, n = what.length(); i < n; ++i) {
                switch (what.charAt(i)) {
                    case 'S':
                        ar.funcinfo(f);
                        break;
                    case 'l':
                        ar.currentline = ci != null && ci.f.isclosure()? ci.currentline(): -1;
                        break;
                    case 'u':
                        if (f != null && f.isclosure()) {
                            Prototype p = f.checkclosure().p;
                            ar.nups = (short) p.upvalues.length;
                            ar.nparams = (short) p.numparams;
                            ar.isvararg = p.is_vararg != 0;
                        } else {
                            ar.nups = 0;
                            ar.isvararg = true;
                            ar.nparams = 0;
                        }
                        break;
                    case 't':
                        ar.istailcall = false;
                        break;
                    case 'n': {
                        /* calling function is a known Lua function? */
                        if (ci != null && ci.previous != null) {
                            if (ci.previous.f.isclosure()) {
                                NameWhat nw = getfuncname(ci.previous);
                                if (nw != null) {
                                    ar.name = nw.name;
                                    ar.namewhat = nw.namewhat;
                                }
                            }
                        }
                        if (ar.namewhat == null) {
                            ar.namewhat = ""; /* not found */
                            ar.name = null;
                        }
                        break;
                    }
                    case 'L':
                    case 'f':
                        break;
                    default:
                        // TODO: return bad status.
                        break;
                }
            }
            return ar;
        }

    }

    public class CallFrame {
        public static final LuaValue[] EMPTY = {};

        public LuaFunction f;
        public int         pc;
        public int         top;
        public Varargs     v;
        public LuaValue[]  stack = EMPTY;
        public CallFrame   previous;

        public void set(LuaClosure function, Varargs varargs, LuaValue[] stack) {
            this.f = function;
            this.v = varargs;
            this.stack = stack;
        }

        public String shortsource() {
            return f.isclosure()? f.checkclosure().p.shortsource(): "[Java]";
        }

        public void set(LuaFunction function) {
            this.f = function;
        }

        public void reset() {
            this.f = null;
            this.v = null;
            this.stack = EMPTY;
        }

        public void instr(int pc, Varargs v, int top) {
            this.pc = pc;
            this.v = v;
            this.top = top;
            if (trace)
                Print.printState(f.checkclosure(), pc, stack, top, v);
        }

        public Varargs getLocal(int i) {
            LuaString name = getlocalname(i);
            if (i >= 1 && i <= stack.length && stack[i-1] != null)
                return varargsOf(name == null? NIL: name, stack[i-1]);
            else
                return NIL;
        }

        public Varargs setLocal(int i, LuaValue value) {
            LuaString name = getlocalname(i);
            if (i >= 1 && i <= stack.length && stack[i-1] != null) {
                stack[i-1] = value;
                return name == null? NIL: name;
            } else {
                return NIL;
            }
        }

        public int currentline() {
            if (!f.isclosure())
                return -1;
            int[] li = f.checkclosure().p.lineinfo;
            return li == null || pc < 0 || pc >= li.length? -1: li[pc];
        }

        public String sourceline() {
            if (!f.isclosure())
                return f.tojstring();
            return f.checkclosure().p.shortsource() + ":" + currentline();
        }

        public int linedefined() {
            return f.isclosure()? f.checkclosure().p.linedefined: -1;
        }

        public LuaString getlocalname(int index) {
            if (!f.isclosure())
                return null;
            return f.checkclosure().p.getlocalname(index, pc);
        }
    }

    record NameWhat(String name, String namewhat) {
    }

    // Return the name info if found, or null if no useful information could be found.
    static NameWhat getfuncname(CallFrame frame) {
        if (!frame.f.isclosure())
            return new NameWhat(frame.f.classnamestub(), "Java");
        Prototype p = frame.f.checkclosure().p;
        int pc = frame.pc;
        int i = p.code[pc]; /* calling instruction */
        LuaString tm;
        switch (Lua.GET_OPCODE(i)) {
            case Lua.OP_CALL, Lua.OP_TAILCALL -> { /* get function name */
                return getobjname(p, pc, Lua.GETARG_A(i));
            }
            case Lua.OP_TFORCALL -> { /* for iterator */
                return new NameWhat("(for iterator)", "(for iterator");
            }
            /* all other instructions can call only through metamethods */
            case Lua.OP_SELF, Lua.OP_GETTABUP, Lua.OP_GETTABLE -> tm = LuaValue.INDEX;
            case Lua.OP_SETTABUP, Lua.OP_SETTABLE -> tm = LuaValue.NEWINDEX;
            case Lua.OP_EQ -> tm = LuaValue.EQ;
            case Lua.OP_ADD -> tm = LuaValue.ADD;
            case Lua.OP_SUB -> tm = LuaValue.SUB;
            case Lua.OP_MUL -> tm = LuaValue.MUL;
            case Lua.OP_DIV -> tm = LuaValue.DIV;
            case Lua.OP_MOD -> tm = LuaValue.MOD;
            case Lua.OP_POW -> tm = LuaValue.POW;
            case Lua.OP_UNM -> tm = LuaValue.UNM;
            case Lua.OP_LEN -> tm = LuaValue.LEN;
            case Lua.OP_LT -> tm = LuaValue.LT;
            case Lua.OP_LE -> tm = LuaValue.LE;
            case Lua.OP_CONCAT -> tm = LuaValue.CONCAT;
            default -> {
                return null; /* else no useful name can be found */
            }
        }
        return new NameWhat(tm.tojstring(), "metamethod");
    }

    // return NameWhat if found, null if not
    public static NameWhat getobjname(Prototype p, int lastpc, int reg) {
        int pc = lastpc; // currentpc(L, ci);
        LuaString name = p.getlocalname(reg+1, pc);
        if (name != null) /* is a local? */
            return new NameWhat(name.tojstring(), "local");

        /* else try symbolic execution */
        pc = findsetreg(p, lastpc, reg);
        if (pc != -1) { /* could find instruction? */
            int i = p.code[pc];
            switch (Lua.GET_OPCODE(i)) {
                case Lua.OP_MOVE -> {
                    int a = Lua.GETARG_A(i);
                    int b = Lua.GETARG_B(i); /* move from `b' to `a' */
                    if (b < a)
                        return getobjname(p, pc, b); /* get name for `b' */
                }
                case Lua.OP_GETTABUP, Lua.OP_GETTABLE -> {
                    int k = Lua.GETARG_C(i); /* key index */
                    int t = Lua.GETARG_B(i); /* table index */
                    LuaString vn = Lua.GET_OPCODE(i) == Lua.OP_GETTABLE /* name of indexed variable */
                            ? p.getlocalname(t + 1, pc)
                            : t < p.upvalues.length ? p.upvalues[t].name : WORD_QMARK;
                    String jname = kname(p, pc, k);
                    return new NameWhat(jname, vn != null && vn.eq_b(ENV) ? "global" : "field");
                }
                case Lua.OP_GETUPVAL -> {
                    int u = Lua.GETARG_B(i); /* upvalue index */
                    name = u < p.upvalues.length ? p.upvalues[u].name : WORD_QMARK;
                    return name == null ? null : new NameWhat(name.tojstring(), "upvalue");
                }
                case Lua.OP_LOADK, Lua.OP_LOADKX -> {
                    int b = Lua.GET_OPCODE(i) == Lua.OP_LOADK ? Lua.GETARG_Bx(i) : Lua.GETARG_Ax(p.code[pc + 1]);
                    if (p.k[b].isstring()) {
                        name = p.k[b].strvalue();
                        return new NameWhat(name.tojstring(), "constant");
                    }
                }
                case Lua.OP_SELF -> {
                    int k = Lua.GETARG_C(i); /* key index */
                    String jname = kname(p, pc, k);
                    return new NameWhat(jname, "method");
                }
                default -> {
                }
            }
        }
        return null; /* no useful name found */
    }

    static String kname(Prototype p, int pc, int c) {
        if (Lua.ISK(c)) { /* is 'c' a constant? */
            LuaValue k = p.k[Lua.INDEXK(c)];
            if (k.isstring()) { /* literal constant? */
                return k.tojstring(); /* it is its own name */
            } /* else no reasonable name found */
        } else { /* 'c' is a register */
            NameWhat what = getobjname(p, pc, c); /* search for 'c' */
            if (what != null && "constant".equals(what.namewhat)) { /* found a constant name? */
                return what.name; /* 'name' already filled */
            }
            /* else no reasonable name found */
        }
        return "?"; /* no reasonable name found */
    }

    /*
     ** try to find last instruction before 'lastpc' that modified register 'reg'
     */
    static int findsetreg(Prototype p, int lastpc, int reg) {
        int pc;
        int setreg = -1; /* keep last instruction that changed 'reg' */
        for (pc = 0; pc < lastpc; pc++) {
            int i = p.code[pc];
            int op = Lua.GET_OPCODE(i);
            int a = Lua.GETARG_A(i);
            switch (op) {
                case Lua.OP_LOADNIL -> {
                    int b = Lua.GETARG_B(i);
                    if (a <= reg && reg <= a + b) /* set registers from 'a' to 'a+b' */
                        setreg = pc;
                }
                case Lua.OP_TFORCALL -> {
                    if (reg >= a + 2)
                        setreg = pc; /* affect all regs above its base */
                }
                case Lua.OP_CALL, Lua.OP_TAILCALL -> {
                    if (reg >= a)
                        setreg = pc; /* affect all registers above base */
                }
                case Lua.OP_JMP -> {
                    int b = Lua.GETARG_sBx(i);
                    int dest = pc + 1 + b;
                    /* jump is forward and do not skip `lastpc'? */
                    if (pc < dest && dest <= lastpc)
                        pc += b; /* do the jump */
                }
                case Lua.OP_TEST -> {
                    if (reg == a)
                        setreg = pc; /* jumped code can change 'a' */
                }
                case Lua.OP_SETLIST -> { // Lua.testAMode(Lua.OP_SETLIST) == false
                    if ((i >> 14 & 0x1ff) == 0)
                        pc++; // if c == 0 then c stored in next op -> skip
                }
                default -> {
                    if (Lua.testAMode(op) && reg == a) /* any instruction that set A */
                        setreg = pc;
                }
            }
        }
        return setreg;
    }
}

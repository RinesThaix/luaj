/*******************************************************************************
* Copyright (c) 2009-2011 Luaj.org. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package org.luaj.vm2.lib.debug;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaNil;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Subclass of {@link LibFunction} which implements the lua standard
 * {@code debug} library.
 * <p>
 * The debug library in luaj tries to emulate the behavior of the corresponding
 * C-based lua library. To do this, it must maintain a separate stack of calls
 * to {@link LuaClosure} and {@link LibFunction} instances. Especially when
 * lua-to-java bytecode compiling is being used via a
 * {@link org.luaj.vm2.Globals.Compiler} such as
 * {@link org.luaj.vm2.luajc.LuaJC}, this cannot be done in all cases.
 * <p>
 * Typically, this library is included as part of a call to either
 * {@link org.luaj.vm2.lib.jse.JsePlatform#debugGlobals()} or
 * {@link org.luaj.vm2.lib.jme.JmePlatform#debugGlobals()}
 *
 * <pre>
 * {
 * 	&#64;code
 * 	Globals globals = JsePlatform.debugGlobals();
 * 	System.out.println(globals.get("debug").get("traceback").call());
 * }
 * </pre>
 * <p>
 * To instantiate and use it directly, link it into your globals table via
 * {@link LuaValue#load(LuaValue)} using code such as:
 *
 * <pre>
 * {
 * 	&#64;code
 * 	Globals globals = new Globals();
 * 	globals.load(new JseBaseLib());
 * 	globals.load(new PackageLib());
 * 	globals.load(new DebugLib());
 * 	System.out.println(globals.get("debug").get("traceback").call());
 * }
 * </pre>
 * <p>
 * This library exposes the entire state of lua code, and provides method to see
 * and modify all underlying lua values within a Java VM so should not be
 * exposed to client code in a shared server environment.
 *
 * @see LibFunction
 * @see org.luaj.vm2.lib.jse.JsePlatform
 * @see org.luaj.vm2.lib.jme.JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.10">Lua 5.2 Debug
 *      Lib Reference</a>
 */
public class DebugLib extends DebugLibBase {

	static final LuaString         LUA    = valueOf("Lua");

	static final LuaString FUNC            = valueOf("func");
	static final LuaString ISTAILCALL      = valueOf("istailcall");
	static final LuaString ISVARARG        = valueOf("isvararg");
	static final LuaString NUPS            = valueOf("nups");
	static final LuaString NPARAMS         = valueOf("nparams");
	static final LuaString NAME            = valueOf("name");
	static final LuaString NAMEWHAT        = valueOf("namewhat");
	static final LuaString WHAT            = valueOf("what");
	static final LuaString SOURCE          = valueOf("source");
	static final LuaString SHORT_SRC       = valueOf("short_src");
	static final LuaString LINEDEFINED     = valueOf("linedefined");
	static final LuaString LASTLINEDEFINED = valueOf("lastlinedefined");
	static final LuaString CURRENTLINE     = valueOf("currentline");
	static final LuaString ACTIVELINES     = valueOf("activelines");

	Globals globals;

	/**
	 * Perform one-time initialization on the library by creating a table
	 * containing the library functions, adding that table to the supplied
	 * environment, adding the table to package.loaded, and returning table as
	 * the return value.
	 *
	 * @param modname the module name supplied if this is loaded via 'require'.
	 * @param env     the environment to load into, which must be a Globals
	 *                instance.
	 */
	@Override
	public LuaValue call(LuaValue modname, LuaValue env) {
		globals = env.checkglobals();
		LuaTable debug = new LuaTable();
		debug.set("debug", new debug());
		debug.set("gethook", new gethook());
		debug.set("getinfo", new getinfo());
		debug.set("getlocal", new getlocal());
		debug.set("getmetatable", new getmetatable());
		debug.set("getregistry", new getregistry());
		debug.set("getupvalue", new getupvalue());
		debug.set("getuservalue", new getuservalue());
		debug.set("sethook", new sethook());
		debug.set("setlocal", new setlocal());
		debug.set("setmetatable", new setmetatable());
		debug.set("setupvalue", new setupvalue());
		debug.set("setuservalue", new setuservalue());
		debug.set("traceback", new traceback());
		debug.set("upvalueid", new upvalueid());
		debug.set("upvaluejoin", new upvaluejoin());
		env.set("debug", debug);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("debug", debug);
		return debug;
	}

	// debug.debug()
	static final class debug extends ZeroArgFunction {
		@Override
		public LuaValue call() {
			return NONE;
		}
	}

	// debug.gethook ([thread])
	final class gethook extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaThread t = args.narg() > 0? args.checkthread(1): globals.running;
			LuaThread.State s = t.state;
			return varargsOf(s.hookfunc != null? s.hookfunc: NIL,
				valueOf((s.hookcall? "c": "")+(s.hookline? "l": "")+(s.hookrtrn? "r": "")), valueOf(s.hookcount));
		}
	}

	//	debug.getinfo ([thread,] f [, what])
	final class getinfo extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int a = 1;
			LuaThread thread = args.isthread(a)? args.checkthread(a++): globals.running;
			LuaValue func = args.arg(a++);
			String what = args.optjstring(a++, "flnStu");
			DebugLib.CallStack callstack = callstack(thread);

			// find the stack info
			DebugLib.CallFrame frame;
			if (func.isnumber()) {
				frame = callstack.getCallFrame(func.toint());
				if (frame == null)
					return NONE;
				func = frame.f;
			} else if (func.isfunction()) {
				frame = callstack.findCallFrame(func);
			} else {
				return argerror(a-2, "function or level");
			}

			// start a table
			DebugInfo ar = callstack.auxgetinfo(what, (LuaFunction) func, frame);
			LuaTable info = new LuaTable();
			if (what.indexOf('S') >= 0) {
				info.set(WHAT, LUA);
				info.set(SOURCE, valueOf(ar.source));
				info.set(SHORT_SRC, valueOf(ar.short_src));
				info.set(LINEDEFINED, valueOf(ar.linedefined));
				info.set(LASTLINEDEFINED, valueOf(ar.lastlinedefined));
			}
			if (what.indexOf('l') >= 0) {
				info.set(CURRENTLINE, valueOf(ar.currentline));
			}
			if (what.indexOf('u') >= 0) {
				info.set(NUPS, valueOf(ar.nups));
				info.set(NPARAMS, valueOf(ar.nparams));
				info.set(ISVARARG, ar.isvararg? ONE: ZERO);
			}
			if (what.indexOf('n') >= 0) {
				info.set(NAME, LuaValue.valueOf(ar.name != null? ar.name: "?"));
				info.set(NAMEWHAT, LuaValue.valueOf(ar.namewhat));
			}
			if (what.indexOf('t') >= 0) {
				info.set(ISTAILCALL, ZERO);
			}
			if (what.indexOf('L') >= 0) {
				LuaTable lines = new LuaTable();
				info.set(ACTIVELINES, lines);
				DebugLib.CallFrame cf;
				for (int l = 1; (cf = callstack.getCallFrame(l)) != null; ++l)
					if (cf.f == func)
						lines.insert(-1, valueOf(cf.currentline()));
			}
			if (what.indexOf('f') >= 0) {
				if (func != null)
					info.set(FUNC, func);
			}
			return info;
		}
	}

	//	debug.getlocal ([thread,] f, local)
	final class getlocal extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int a = 1;
			LuaThread thread = args.isthread(a)? args.checkthread(a++): globals.running;
			LuaValue func = args.arg(a++);
			int local = args.checkint(a);

			if (func.isfunction())
				return func.checkclosure().p.getlocalname(local, 0);

			// find the stack info
			DebugLib.CallFrame frame = callstack(thread).getCallFrame(func.checkint());
			if (frame == null)
				return argerror(a, "level out of range");
			return frame.getLocal(local);
		}
	}

	//	debug.getmetatable (value)
	static final class getmetatable extends LibFunction {
		@Override
		public LuaValue call(LuaValue v) {
			LuaValue mt = v.getmetatable();
			return mt != null? mt: NIL;
		}
	}

	//	debug.getregistry ()
	final class getregistry extends ZeroArgFunction {
		@Override
		public LuaValue call() {
			return globals;
		}
	}

	//	debug.getupvalue (f, up)
	static final class getupvalue extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue func = args.checkfunction(1);
			int up = args.checkint(2);
			if (func instanceof LuaClosure) {
				LuaClosure c = (LuaClosure) func;
				LuaString name = findupvalue(c, up);
				if (name != null) {
					return varargsOf(name, c.upValues[up-1].getValue());
				}
			}
			return NIL;
		}
	}

	//	debug.getuservalue (u)
	static final class getuservalue extends LibFunction {
		@Override
		public LuaValue call(LuaValue u) {
			return u.isuserdata()? u: NIL;
		}
	}

	// debug.sethook ([thread,] hook, mask [, count])
	final class sethook extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int a = 1;
			LuaThread t = args.isthread(a)? args.checkthread(a++): globals.running;
			LuaValue func = args.optfunction(a++, null);
			String str = args.optjstring(a++, "");
			int count = args.optint(a++, 0);
			boolean call = false, line = false, rtrn = false;
			for (int i = 0; i < str.length(); i++)
				switch (str.charAt(i)) {
				case 'c':
					call = true;
					break;
				case 'l':
					line = true;
					break;
				case 'r':
					rtrn = true;
					break;
				}
			LuaThread.State s = t.state;
			s.hookfunc = func;
			s.hookcall = call;
			s.hookline = line;
			s.hookcount = count;
			s.hookrtrn = rtrn;
			return NONE;
		}
	}

	//	debug.setlocal ([thread,] level, local, value)
	final class setlocal extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int a = 1;
			LuaThread thread = args.isthread(a)? args.checkthread(a++): globals.running;
			int level = args.checkint(a++);
			int local = args.checkint(a++);
			LuaValue value = args.arg(a++);
			CallFrame f = callstack(thread).getCallFrame(level);
			return f != null? f.setLocal(local, value): NONE;
		}
	}

	//	debug.setmetatable (value, table)
	static final class setmetatable extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue value, LuaValue table) {
			LuaValue mt = table.opttable(null);
			switch (value.type()) {
			case TNIL:
				LuaNil.s_metatable = mt;
				break;
			case TNUMBER:
				LuaNumber.s_metatable = mt;
				break;
			case TBOOLEAN:
				LuaBoolean.s_metatable = mt;
				break;
			case TSTRING:
				LuaString.s_metatable = mt;
				break;
			case TFUNCTION:
				LuaFunction.s_metatable = mt;
				break;
			case TTHREAD:
				LuaThread.s_metatable = mt;
				break;
			default:
				value.setmetatable(mt);
			}
			return value;
		}
	}

	//	debug.setupvalue (f, up, value)
	static final class setupvalue extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue value = args.checkvalue(3);
			int up = args.checkint(2);
			LuaValue func = args.checkfunction(1);
			if (func instanceof LuaClosure) {
				LuaClosure c = (LuaClosure) func;
				LuaString name = findupvalue(c, up);
				if (name != null) {
					c.upValues[up-1].setValue(value);
					return name;
				}
			}
			return NIL;
		}
	}

	//	debug.setuservalue (udata, value)
	static final class setuservalue extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			Object o = args.checkuserdata(1);
			LuaValue v = args.checkvalue(2);
			LuaUserdata u = (LuaUserdata) args.arg1();
			u.m_instance = v.checkuserdata();
			u.m_metatable = v.getmetatable();
			return NONE;
		}
	}

	//	debug.traceback ([thread,] [message [, level]])
	final class traceback extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int a = 1;
			LuaThread thread = args.isthread(a)? args.checkthread(a++): globals.running;
			String message = args.optjstring(a++, null);
			int level = args.optint(a++, 1);
			String tb = callstack(thread).traceback(level);
			return valueOf(message != null? message + "\n" + tb: tb);
		}
	}

	//	debug.upvalueid (f, n)
	static final class upvalueid extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int up = args.checkint(2);
			LuaValue func = args.checkfunction(1);
			if (func instanceof LuaClosure) {
				LuaClosure c = (LuaClosure) func;
				if (c.upValues != null && up > 0 && up <= c.upValues.length) {
					return valueOf(c.upValues[up-1].hashCode());
				}
			}
			return NIL;
		}
	}

	//	debug.upvaluejoin (f1, n1, f2, n2)
	static final class upvaluejoin extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			int n1 = args.checkint(2);
			LuaClosure f1 = args.checkclosure(1);
			int n2 = args.checkint(4);
			LuaClosure f2 = args.checkclosure(3);
			if (n1 < 1 || n1 > f1.upValues.length)
				argerror("index out of range");
			if (n2 < 1 || n2 > f2.upValues.length)
				argerror("index out of range");
			f1.upValues[n1-1] = f2.upValues[n2-1];
			return NONE;
		}
	}

	static LuaString findupvalue(LuaClosure c, int up) {
		if (c.upValues != null && up > 0 && up <= c.upValues.length) {
			if (c.p.upvalues != null && up <= c.p.upvalues.length)
				return c.p.upvalues[up-1].name;
			else
				return LuaString.valueOf("." + up);
		}
		return null;
	}

}

/**
 * 
 */
package lua;

import lua.value.LFunction;
import lua.value.LString;
import lua.value.LTable;
import lua.value.LValue;

final class Builtin extends LFunction {

	static void addBuiltins(LTable table) {
		for ( int i=0; i<NAMES.length; i++ )
			table.luaSetTable( new LString( NAMES[i] ), new Builtin(i) );
	}

	private static final int PRINT = 0;
	private static final int PAIRS = 1;
	
	private static final String[] NAMES = { "print", "pairs" };
	
	private int id;
	private Builtin( int id ) {			
		this.id = id;
	}

	public String toString() {
		return "Builtin('"+NAMES[id]+"')";
	}
	
	// perform a lua call
	public void luaStackCall(StackState state, int base, int nargs) {
		switch ( id ) {
		case PRINT:
			for ( int i=0; i<nargs; i++ ) {
				if ( i > 0 )
					System.out.print( "\t" );
				System.out.print( String.valueOf(state.stack[base+1+i]) );
			}
			System.out.println();
			return;
		case PAIRS:
			LValue value = state.stack[base+1].luaPairs();
			state.stack[base] = value;
			return;
		default:
			luaUnsupportedOperation();
		}
	}

}
package org.tekila.lombokizer.handlers;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jst.ws.annotations.core.AnnotationsCore;
import org.eclipse.jst.ws.annotations.core.utils.AnnotationUtils;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class LombokizerHandler extends AbstractHandler {
	/**
	 * The constructor.
	 */
	public LombokizerHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		ISelection sel = HandlerUtil.getActiveMenuSelection(event);
		IStructuredSelection selection = (IStructuredSelection) sel;

		@SuppressWarnings("rawtypes")
		Iterator iterator = selection.iterator();
		
		while (iterator.hasNext()) {
			Object obj = iterator.next();
			if (obj instanceof ICompilationUnit) {
				ICompilationUnit compil = (ICompilationUnit) obj;
				try {
					lombokizeClass(shell, compil);
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			} 
			if (obj instanceof IPackageFragment) {
				IPackageFragment pf = (IPackageFragment) obj;
				try {
					ICompilationUnit[] units = pf.getCompilationUnits();
					for (ICompilationUnit cu : units) {
						try {
							lombokizeClass(shell, cu);
						} catch (JavaModelException ee) {
							ee.printStackTrace();
						}
						
					}
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	
	private void lombokizeClass(Shell shell, ICompilationUnit unit) throws JavaModelException {
		unit.becomeWorkingCopy(null);
		
		// compilation unit from the AST. will be lazy-inited
		AST ast = null;
		
		try {
			IType[] types = unit.getAllTypes();
			for (IType t : types) {
				boolean deletedAccessors = processFields(t);
				
				if (deletedAccessors) {
					if (ast == null) {
						ast = parseAST(unit);
					}
					
					// add annotation on class
					Annotation ann = AnnotationsCore.createMarkerAnnotation(ast,
							"Getter");
					AnnotationUtils.addAnnotation(t, ann);
					ann = AnnotationsCore.createMarkerAnnotation(ast,
							"Setter");
					AnnotationUtils.addAnnotation(t, ann);
					
					// add imports for lombok annotations
					AnnotationUtils.addImport(t, "lombok.Getter");
					AnnotationUtils.addImport(t, "lombok.Setter");
				}
			}
			
			// reconcile ?
			unit.commitWorkingCopy(true, null);
		} catch (CoreException e) {
			e.printStackTrace();
		} finally {
			unit.discardWorkingCopy();
		}
	}

	private AST parseAST(ICompilationUnit unit) {
		AST ast;
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(unit);
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);
		ast = cu.getAST();
		return ast;
	}

	/**
	 * Delete all accessors
	 * @param t
	 * @return true if accessors were deleted
	 * @throws JavaModelException
	 */
	private boolean processFields(IType t) throws JavaModelException {
		IField[] fields = t.getFields();
		IMethod[] methods = t.getMethods();
		
		boolean deletedAccessors = false;
		for (IField f : fields) {
			// skip static or non-private field (constants, protected)
			int flags = f.getFlags();
			if (Flags.isStatic(flags) ||
					!Flags.isPrivate(flags)) {
				continue;
			}

			String fieldName = f.getElementName();
			
			IMethod getter = null;
			IMethod setter = null;
			
			for (IMethod m : methods) {
				if (isGetterOf(m, fieldName)) {
					getter = m;
				}
				if (isSetterOf(m, fieldName)) {
					setter = m;
				}
			}
			
			// delete found methods
			if (getter != null) {
				getter.delete(true, null);
				deletedAccessors = true;
			}
			if (setter != null) {
				setter.delete(true, null);
				deletedAccessors = true;
			}
		}
		return deletedAccessors;
	}

	
	private boolean isSetterOf(IMethod m, String fieldName) {
		String name = m.getElementName();
		return name.equalsIgnoreCase("get" + fieldName) || name.equalsIgnoreCase("is" + fieldName);
	}

	private boolean isGetterOf(IMethod m, String fieldName) {
		String name = m.getElementName();
		return name.equalsIgnoreCase("set" + fieldName);
	}

}

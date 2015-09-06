/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.flux.client.util;

/**
 * A listener is an entity that is interested to track the value of an observable.
 */
public interface Listener<T> {

	/**
	 * Called when an Observable this listener is attached has a 'new' value for
	 * this listener. This method is called by the Observable's on all its attached
	 * listeners whenever its value is changed. It is also called initially, when
	 * a listener is added to the Observable.
	 * <p>
	 * This means that listeners attached to an Observable are guaranteed to be called
	 * at least once by that Observable even if its value never changes.
	 */
	public void newValue(Observable<T> o, T v);

}

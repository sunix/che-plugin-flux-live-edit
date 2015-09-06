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
 * An 'Observable' is a value that may change over time. The Observable interface provides
 * a way to observe the current value as well as attach listeners (Observers) that notified
 * when the value changes.
 */
public interface Observable<V> {

	/**
	 * Retrieves the current value.
	 */
	public V getValue();

	public void addListener(Listener<V> l);
	public void removeListener(Listener<V> l);


}

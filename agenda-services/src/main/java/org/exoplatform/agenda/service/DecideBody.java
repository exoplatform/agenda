/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.service;

/**
 * A decide step's reads and writes.
 *
 * @param <T> the result type
 * @param <X1> a checked exception it may throw
 * @param <X2> another checked exception it may throw
 */
@FunctionalInterface
interface DecideBody<T, X1 extends Exception, X2 extends Exception> {

  T decide() throws X1, X2;
}

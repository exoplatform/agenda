/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * The style of a round swatch of the given size: the colour as its fill, or a
 * dashed ring when there is no colour, as forms draws its option colours.
 *
 * @param {String} color the colour shown, or nothing
 * @param {Number} size the diameter in pixels
 * @returns {Object} the inline style of the swatch
 */
export function swatchStyle(color, size) {
  const style = {width: `${size}px`, height: `${size}px`, flex: `0 0 ${size}px`};
  if (color) {
    style.backgroundColor = color;
  } else {
    style.border = '1px dashed currentColor';
  }
  return style;
}

/**
 * Compares two colours as the platform picker emits them: #RRGGBB in upper
 * case, a stored #rgb or lower-case code being the same colour.
 *
 * @param {String} first a colour
 * @param {String} second another colour
 * @returns {Boolean} true when both are the same colour
 */
export function sameColor(first, second) {
  return normalizeColor(first) === normalizeColor(second);
}

/**
 * @param {String} color a colour code, with or without its #
 * @returns {String} its six upper-case hex digits, or the code as given when it is no #rgb or #rrggbb
 */
function normalizeColor(color) {
  let hex = String(color || '').trim().replace('#', '').toUpperCase();
  if (hex.length === 3) {
    hex = hex.split('').map(digit => digit + digit).join('');
  }
  return hex;
}

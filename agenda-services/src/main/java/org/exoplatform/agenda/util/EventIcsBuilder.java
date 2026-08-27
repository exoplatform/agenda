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
package org.exoplatform.agenda.util;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.commons.utils.HTMLEntityEncoder;

/**
 * What every iCalendar document eXo writes about a meeting says the same way.
 *
 * <p>
 * eXo describes one meeting as iCalendar through two channels, and they must
 * not drift apart again. The <b>mail</b> channel attaches a document to the
 * notification that announces an event ({@link Utils#generateIcsFile}); the
 * <b>calendar copy</b> channel pushes an object into the user's own calendar
 * over CalDAV (the caldav-integration addon). They were written separately,
 * they diverged, and five payload defects fixed in one of them had to be
 * looked for by hand in the other. This class is where the answer to "do they
 * say the same thing?" stops depending on anybody remembering to look.
 *
 * <p>
 * <b>The two outputs are deliberately not the same document</b>, and this class
 * does not try to make them one. The mailed document carries no attendees at
 * all — answering happens through the tokenised links in the mail body, not
 * through iMIP (EXO-89705) — while the copy must carry them with their
 * PARTSTAT, because a roster the client recognises is the only thing that
 * makes it offer RSVP (EXO-89681). What is shared is the <i>core</i>: what the
 * meeting is called, what it is about, where it came from, and — since
 * EXO-89751 — where it lives, the <code>URL</code> both channels now write and
 * both channels name in the description. What stays with
 * each channel is who is written into it and how it is to be acted on:
 * attendees and PARTSTAT, SCHEDULE-AGENT, alarms, STATUS and TRANSP, and
 * METHOD — which the mail declares as PUBLISH and which a calendar object
 * resource must not declare at all (RFC 4791 &sect;4.1).
 *
 * <p>
 * The description is the piece that matters most here, and the reason is worth
 * stating. The mail said "Invitation sent by X in space Chemistry"; the copy
 * said nothing at all — no attribution, often no DESCRIPTION whatsoever. That
 * is backwards. A reader opening the mail already knows where they are; the
 * copy sits in their calendar among fifty other entries with nothing to say
 * which system put it there or which space it belongs to. Written once, here,
 * both channels say it.
 *
 * <p>
 * This class lives in agenda rather than in caldav-integration because the
 * dependency runs one way only: caldav-integration builds on agenda and never
 * the reverse. caldav-integration passes in what only it knows.
 */
public final class EventIcsBuilder {

  /** Label introducing who sent the invitation. */
  private static final String INVITATION_TEXT_LABEL = "agenda.invitationText";

  /** Label introducing the space the event belongs to. */
  private static final String IN_SPACE_LABEL        = "agenda.inSpace";

  /** Label introducing the conference link. */
  private static final String VISIO_LINK_LABEL      = "agenda.visioLink";

  /** Label introducing the link back to the event in eXo. */
  private static final String EVENT_LINK_LABEL      = "agenda.eventLink";

  /** Label introducing the event's own description. */
  private static final String EVENT_DETAIL_LABEL    = "agenda.eventDetail";

  /** Label introducing the block of answer links. */
  private static final String RSVP_PROMPT_LABEL     = "agenda.rsvpPrompt";

  /**
   * The answers offered in the description, in the order they are written.
   *
   * <p>
   * Fixed rather than derived from the map handed in, and that is not
   * cosmetic: the description of a calendar copy is compared byte for byte by
   * the mirror, so an iteration order that varied between renders would rewrite
   * every copy on every sweep. Accept first, since it is the answer most people
   * are looking for; Decline last, so a mis-click is the hardest of the three.
   */
  private static final EventAttendeeResponse[] RSVP_ORDER =
                                                          {EventAttendeeResponse.ACCEPTED,
                                                              EventAttendeeResponse.TENTATIVE,
                                                              EventAttendeeResponse.DECLINED};

  /**
   * Placeholder standing in for a line break while Jsoup is asked for text.
   *
   * <p>
   * Jsoup drops the layout together with the markup, so the breaks a reader
   * relies on are pinned as text nodes before the text is read out.
   */
  private static final String LINE_BREAK_MARKER     = "\u0001";

  /**
   * Not instantiable: this is a builder of values, holding no state of its own.
   */
  private EventIcsBuilder() {
    // Utility class.
  }

  /**
   * The link that opens this event in eXo, derived from the event itself.
   *
   * <p>
   * <b>Derived, never passed in.</b> The CalDAV channel used to take this value
   * from whatever the browser had put on the push request, which meant only a
   * browser push carried it: every sweep and every repair rendered the copy with
   * no <code>URL</code> at all, so the link appeared once and the next repair
   * stripped it — and the mirror comparison had to ignore the property outright,
   * because comparing a value one side never renders makes every copy look
   * rewritten. Deriving it here is what fixes both: the same event renders the
   * same string on a browser push, on a sweep, on a repair and in the mailed
   * document, so there is nothing left to lose and nothing left to exempt
   * (EXO-89751, EXO-89716).
   *
   * <p>
   * The shape is not invented here. It is
   * {@link NotificationUtils#getEventURL(long)} — the one already in the body of
   * every notification mail about this event, and the same
   * <code>agenda?eventId=</code> route the browser was building for itself.
   *
   * <p>
   * It is guarded like {@link #label}, and for the same reason: the address
   * needs the portal's configured domain and its meta portal, neither of which
   * is resolvable outside a running container. A copy that reached nobody's
   * calendar because a link could not be composed would be a worse outcome than
   * a copy without a link, and this builder is on the push path.
   *
   * @param eventId technical identifier of the event, 0 or less when the caller
   *          has none
   * @return the absolute link, or null when there is no event to name or the
   *         portal cannot be asked
   */
  public static String eventUrl(long eventId) {
    if (eventId <= 0) {
      return null;
    }
    try {
      return StringUtils.trimToNull(NotificationUtils.getEventURL(eventId));
    } catch (RuntimeException | LinkageError e) {
      return null;
    }
  }

  /**
   * The plain-text DESCRIPTION both channels write, attribution included.
   *
   * <p>
   * It is built from the raw labels and values rather than by unescaping the
   * HTML flavour below. Going through the HTML runs every label through
   * {@link HTMLEntityEncoder}, so a French label arrives as
   * <code>envoy&amp;eacute;e</code>, whose semicolon the iCalendar writer then
   * escapes again into <code>envoy&amp;eacute\;e</code> — an accented word
   * mangled twice over. Reading from the source instead means the value is
   * plain UTF-8 whatever the locale.
   *
   * <p>
   * DESCRIPTION is plain text by definition (RFC 5545 &sect;3.8.1.5); the HTML
   * flavour belongs to X-ALT-DESC alone, which is why the two are built by two
   * methods sitting next to each other rather than by one deriving the other.
   *
   * @param userLocale locale the labels are read in; the platform default when
   *          null
   * @param eventCreatorFullName display name of whoever called the meeting,
   *          blank when it cannot be resolved
   * @param spaceName display name of the space the event belongs to, blank for
   *          an event that belongs to no space
   * @param conferenceUrl conference link, blank when the event has none
   * @param eventUrl link back to the event in eXo, from {@link #eventUrl};
   *          <b>blank deliberately for a recipient who has no eXo account</b>,
   *          for whom it resolves to a login screen (EXO-89751)
   * @param rsvpLinks the answer links to offer, keyed by the answer they
   *          record, <b>built for the one recipient this document is for</b>;
   *          null or empty for a channel that offers no answer links, which is
   *          every channel except the calendar copy (EXO-89753)
   * @param eventDescriptionHtml the event's own description, HTML as the editor
   *          stored it, blank when the event has none
   * @return the description as plain text, with real line breaks
   */
  public static String description(Locale userLocale,
                                   String eventCreatorFullName,
                                   String spaceName,
                                   String conferenceUrl,
                                   String eventUrl,
                                   Map<EventAttendeeResponse, String> rsvpLinks,
                                   String eventDescriptionHtml) {
    StringBuilder text = new StringBuilder();
    text.append(label(userLocale, INVITATION_TEXT_LABEL))
        .append(" ")
        .append(StringUtils.defaultString(eventCreatorFullName));
    // An event on a personal calendar belongs to no space, and saying "in
    // space" with nothing after it — or worse, with the word "null", which is
    // what concatenating an absent name produces — is not attribution. The
    // clause is dropped instead.
    if (StringUtils.isNotBlank(spaceName)) {
      text.append(" ").append(label(userLocale, IN_SPACE_LABEL)).append(" ").append(spaceName);
    }
    text.append(".");
    if (StringUtils.isNotBlank(conferenceUrl)) {
      text.append("\n\n").append(label(userLocale, VISIO_LINK_LABEL)).append(" ").append(conferenceUrl);
    }
    // Beside the conference link, and for the same reason it is there: many
    // calendar clients never show the URL property, and the description is
    // what a person actually reads. A copy that says "invitation sent by X in
    // space Chemistry" and offers no way to reach that space names a place the
    // reader cannot get to (EXO-89751).
    if (StringUtils.isNotBlank(eventUrl)) {
      text.append("\n\n").append(label(userLocale, EVENT_LINK_LABEL)).append(" ").append(eventUrl);
    }
    appendRsvpLinks(text, userLocale, rsvpLinks);
    if (StringUtils.isNotBlank(eventDescriptionHtml)) {
      String detail = htmlToPlainText(eventDescriptionHtml);
      if (StringUtils.isNotBlank(detail)) {
        text.append("\n\n").append(label(userLocale, EVENT_DETAIL_LABEL)).append("\n").append(detail);
      }
    }
    return text.toString();
  }

  /**
   * Writes the answer links into the description, one line per answer.
   *
   * <p>
   * <b>Why a description at all, when calendars have RSVP built in.</b> Some
   * clients will never offer Accept or Decline on the eXo Meetings copy.
   * BlueMind's web UI is the confirmed case: it renders the RSVP control only
   * for events in the account's <i>default</i> calendar, and a calendar created
   * over CalDAV is marked non-default in its code, so <code>exo-meetings</code>
   * can never show it - established from BlueMind's own source, and not fixable
   * from our side. A description, on the other hand, is rendered by every
   * client there is (EXO-89753).
   *
   * <p>
   * <b>Links only. The current answer is deliberately not written here.</b> It
   * would be stale the moment it was written: eXo rewrites the copy within
   * seconds of a click (EXO-89715), but the user's client only sees that at its
   * own refresh - minutes away, or a manual reload. A description stating an
   * answer the user has just changed reads as the click having failed, and
   * invites a second click. The answer already has a home the client
   * understands: <code>PARTSTAT</code> on the attendee line. <b>Actions in the
   * description, state in PARTSTAT</b> - one fact, one place.
   *
   * <p>
   * <b>These links are one person's.</b> Each carries a token minted for the
   * recipient whose calendar this copy is being written into, so the caller
   * must build them from the identity it is pushing for and no other. Writing
   * one attendee's token into another attendee's copy would hand over the
   * ability to answer as them.
   *
   * @param text the description being built, appended to in place
   * @param userLocale locale the labels are read in, the platform default when
   *          null
   * @param rsvpLinks the answer links keyed by the answer they record, null or
   *          empty when the channel offers none
   */
  private static void appendRsvpLinks(StringBuilder text, Locale userLocale, Map<EventAttendeeResponse, String> rsvpLinks) {
    if (rsvpLinks == null || rsvpLinks.isEmpty()) {
      return;
    }
    StringBuilder block = new StringBuilder();
    for (EventAttendeeResponse response : RSVP_ORDER) {
      String url = rsvpLinks.get(response);
      if (StringUtils.isNotBlank(url)) {
        block.append("\n").append(label(userLocale, responseLabelKey(response))).append(" ").append(url);
      }
    }
    // A prompt introducing nothing would be worse than no prompt. The map can
    // legitimately arrive with no usable link in it: a token is not minted for
    // an event that carries no date to bound it by (EXO-89752).
    if (block.length() > 0) {
      text.append("\n\n").append(label(userLocale, RSVP_PROMPT_LABEL)).append(block);
    }
  }

  /**
   * Gives the resource bundle key naming one answer.
   *
   * <p>
   * The same three keys the confirmation page reads, so the word a person
   * clicks in their calendar is the word the page tells them was recorded.
   *
   * @param response the answer to label
   * @return the key of its label in the <code>locale.portlet.Agenda</code>
   *         bundle
   */
  private static String responseLabelKey(EventAttendeeResponse response) {
    if (response == EventAttendeeResponse.ACCEPTED) {
      return "agenda.accepted";
    } else if (response == EventAttendeeResponse.DECLINED) {
      return "agenda.declined";
    }
    return "agenda.tentative";
  }

  /**
   * The HTML flavour of the same blurb, the one X-ALT-DESC carries.
   *
   * <p>
   * Only the mail channel writes it: a CalDAV object resource has no use for
   * a non-standard alternate description, and the clients that read a copy
   * show DESCRIPTION. It lives here beside its plain-text twin all the same,
   * because the two are two renderings of one text and separating them is how
   * they drift.
   *
   * <p>
   * <b>It takes no answer links, and that is not an omission.</b> Only the
   * calendar copy carries them (EXO-89753), for a reason specific to it: its
   * client may offer no RSVP control of its own. A mail already carries its
   * Accept and Decline buttons in the body, and the mailed document is a
   * PUBLISH with no attendees on it. Adding a parameter here that every caller
   * would pass as null would be a signature saying something the class does
   * not do.
   *
   * @param userLocale locale the labels are read in; the platform default when
   *          null
   * @param eventCreatorFullName display name of whoever called the meeting
   * @param spaceName display name of the space, blank for none
   * @param conferenceUrl conference link, blank when the event has none
   * @param eventUrl link back to the event in eXo, blank for a recipient with
   *          no eXo account
   * @param eventDescriptionHtml the event's own description as HTML, blank
   *          when the event has none
   * @return the description as an HTML document, on a single line
   */
  public static String htmlDescription(Locale userLocale,
                                       String eventCreatorFullName,
                                       String spaceName,
                                       String conferenceUrl,
                                       String eventUrl,
                                       String eventDescriptionHtml) {
    HTMLEntityEncoder encoder = HTMLEntityEncoder.getInstance();
    StringBuilder html = new StringBuilder("<html><body>");
    html.append(encoder.encodeHTML(label(userLocale, INVITATION_TEXT_LABEL)))
        .append("  <b>")
        .append(StringUtils.defaultString(eventCreatorFullName))
        .append("</b> ");
    if (StringUtils.isNotBlank(spaceName)) {
      html.append(encoder.encodeHTML(label(userLocale, IN_SPACE_LABEL))).append(" <b>").append(spaceName).append("</b>");
    }
    html.append(". ");
    if (StringUtils.isNotBlank(conferenceUrl)) {
      html.append("<br><br><b>")
          .append(encoder.encodeHTML(label(userLocale, VISIO_LINK_LABEL)))
          .append(" </b> <a href=\"")
          .append(conferenceUrl)
          .append("\">")
          .append(conferenceUrl)
          .append("</a>");
    }
    if (StringUtils.isNotBlank(eventUrl)) {
      html.append("<br><br><b>")
          .append(encoder.encodeHTML(label(userLocale, EVENT_LINK_LABEL)))
          .append(" </b> <a href=\"")
          .append(eventUrl)
          .append("\">")
          .append(eventUrl)
          .append("</a>");
    }
    if (StringUtils.isNotBlank(eventDescriptionHtml)) {
      html.append("<br><br>")
          .append(encoder.encodeHTML(label(userLocale, EVENT_DETAIL_LABEL)))
          .append("<br>")
          .append(Utils.escapeEmoticons(eventDescriptionHtml));
    }
    html.append("</body></html>");
    return html.toString().trim().replace("\n", "");
  }

  /**
   * Turns a bare mail address into the CAL-ADDRESS URI RFC 5545 &sect;3.3.3
   * requires for ORGANIZER and ATTENDEE.
   *
   * <p>
   * A bare address is not a URI: it has no scheme, so a client that validates
   * the value simply drops the property — and with ORGANIZER goes any
   * attribution of the invitation, while with ATTENDEE goes the roster that is
   * the only reason a client offers RSVP on a copy.
   *
   * <p>
   * An address that already carries the scheme is left alone. That case is not
   * hypothetical on the copy channel: the address written for the account's own
   * owner is whatever their CalDAV account answers to, which is configuration
   * rather than a profile field, and a value pasted in with its scheme would
   * otherwise become <code>mailto:mailto:...</code>.
   *
   * @param email mail address, already known to be non blank
   * @return the same address as a <code>mailto:</code> URI, left untouched when
   *         it already carries that scheme
   */
  public static URI calendarUserAddress(String email) {
    String address = email.trim();
    if (StringUtils.startsWithIgnoreCase(address, "mailto:")) {
      return URI.create(address);
    }
    return URI.create("mailto:" + address);
  }

  /**
   * Renders an HTML fragment as the plain text a calendar client would show.
   *
   * <p>
   * Block boundaries and <code>&lt;br&gt;</code> become real line breaks,
   * every other tag is dropped and every entity is decoded, so an accented
   * character written as <code>&amp;eacute;</code> comes back as the character
   * itself.
   *
   * @param html HTML fragment, blank tolerated
   * @return the fragment as plain text, empty when there was nothing to render
   */
  public static String htmlToPlainText(String html) {
    if (StringUtils.isBlank(html)) {
      return "";
    }
    Document document = Jsoup.parseBodyFragment(html);
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));
    document.select("br").after(LINE_BREAK_MARKER);
    document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote, pre").after(LINE_BREAK_MARKER);
    String text = document.body().wholeText().replace(LINE_BREAK_MARKER, "\n").replace('\u00A0', ' ');
    return text.replaceAll("[ \\t]*\\R[ \\t]*", "\n").replaceAll("\n{3,}", "\n\n").trim();
  }

  /**
   * Reads one label in the reader's own language.
   *
   * <p>
   * {@link Utils#getResourceBundleLabel} already answers the key itself when
   * the bundle has no entry for it; what is guarded here is the bundle service
   * being unavailable altogether, which it is outside a running portal
   * container. The guard covers a {@link LinkageError} as well as a runtime
   * failure, because the bundle service is resolved through the container's
   * plugin chain and a plugin class absent from the classpath is not a runtime
   * exception. A description degraded to its keys is a poor description; a
   * meeting that never reached the user's calendar because a label could not
   * be read is a lost meeting, and this builder is on the push path.
   *
   * @param userLocale locale to read in, the platform default when null
   * @param key resource bundle key
   * @return the label, or the key itself when it cannot be read
   */
  private static String label(Locale userLocale, String key) {
    try {
      return Utils.getResourceBundleLabel(userLocale == null ? Locale.getDefault() : userLocale, key);
    } catch (RuntimeException | LinkageError e) {
      return key;
    }
  }
}

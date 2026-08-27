package org.exoplatform.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Locale;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * Pins the escaping of the page a guest lands on after answering an invitation
 * from the mail.
 *
 * <p>That page is the only thing an external person sees on eXo's behalf, so its
 * text has to read as a sentence. It reached them as "recorded&amp;#x3a; Accepted"
 * because the page was escaped with an encoder that also escapes ordinary
 * punctuation.</p>
 */
public class GuestResponsePageTest {

  /**
   * Punctuation survives, markup does not.
   *
   * <p>Asserted on the escaper rather than on the rendered page: under a bare unit
   * test the resource bundle does not resolve, so the page carries label keys and
   * contains no punctuation to check.</p>
   */
  @Test
  public void punctuationSurvivesEscapingButMarkupDoesNot() {
    assertEquals("a colon is not markup and must be left alone",
                 "Your answer has been recorded: Accepted",
                 Utils.escapeHtmlText("Your answer has been recorded: Accepted"));

    assertEquals("an apostrophe in a translated sentence must not be mangled either",
                 "l&#39;espace",
                 Utils.escapeHtmlText("l'espace"));

    assertEquals("markup is still escaped, since translations are only as trusted as Crowdin",
                 "&lt;script&gt;alert(1)&lt;/script&gt; &amp; more",
                 Utils.escapeHtmlText("<script>alert(1)</script> & more"));

    assertEquals("a null value renders as nothing rather than as \"null\"", "", Utils.escapeHtmlText(null));
  }

  /**
   * The document itself must not be escaped — a guard against applying the
   * escaper to the whole page instead of to the values inside it.
   */
  @Test
  public void theConfirmationPageIsAWellFormedDocument() {
    String page = Utils.buildGuestResponseConfirmationPage(EventAttendeeResponse.ACCEPTED, Locale.ENGLISH);

    assertFalse("no numeric character reference of the colon anywhere: " + page, page.contains("&#x3a;"));
    assertFalse("nor its decimal form: " + page, page.contains("&#58;"));
    assertEquals("the page is a document, not an escaped string", true, page.startsWith("<!DOCTYPE html>"));
    assertEquals("the page closes properly", true, page.trim().endsWith("</html>"));
  }

  /**
   * The page shown for a lapsed link carries a way into eXo.
   *
   * <p>
   * The point of the page over a bare 401 is that its reader is left with
   * somewhere to go, so the anchor is the thing worth pinning.
   */
  @Test
  public void theExpiredInvitationPageOffersAWayIntoExo() {
    String page = Utils.buildInvitationExpiredPage(Locale.ENGLISH, "https://exo.example.com/portal/dw/agenda?eventId=42");

    assertEquals("the page is a document", true, page.startsWith("<!DOCTYPE html>"));
    assertEquals("the page closes properly", true, page.trim().endsWith("</html>"));
    assertEquals("the link into eXo is rendered as a real anchor",
                 true,
                 page.contains("<a href=\"https://exo.example.com/portal/dw/agenda?eventId=42\""));
  }

  /**
   * With no address to offer, the page renders without an anchor rather than
   * with an empty one.
   *
   * <p>
   * The address needs the portal's configured domain, which is not resolvable
   * everywhere the page can be built, so "no link" is a real case and not a
   * defensive one.
   */
  @Test
  public void theExpiredInvitationPageSurvivesHavingNoLinkToOffer() {
    String page = Utils.buildInvitationExpiredPage(Locale.ENGLISH, null);

    assertFalse("no dangling anchor when there is no address to point at", page.contains("<a "));
    assertEquals("the page is still a document", true, page.startsWith("<!DOCTYPE html>"));
  }
}

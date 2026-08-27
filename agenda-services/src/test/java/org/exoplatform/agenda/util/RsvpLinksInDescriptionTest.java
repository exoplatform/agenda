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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;

/**
 * Pins the answer links the calendar copy carries in its description.
 *
 * <p>
 * The links exist because some clients will never offer an RSVP control on the
 * eXo Meetings copy - BlueMind's web UI renders one only for the account's
 * default calendar, and a calendar created over CalDAV is never default
 * (EXO-89753).
 */
public class RsvpLinksInDescriptionTest {

  private static final String ACCEPT_URL   = "https://exo.example.com/portal/rest/v1/agenda/events/42/response/send"
      + "?response=ACCEPTED&token=AAA&redirect=true";

  private static final String TENTATIVE_URL = "https://exo.example.com/portal/rest/v1/agenda/events/42/response/send"
      + "?response=TENTATIVE&token=BBB&redirect=true";

  private static final String DECLINE_URL  = "https://exo.example.com/portal/rest/v1/agenda/events/42/response/send"
      + "?response=DECLINED&token=CCC&redirect=true";

  /**
   * Builds the three answer links in a map of the given kind.
   *
   * @param links the map to fill, so a caller can choose its iteration order
   * @return the same map, carrying the three links
   */
  private Map<EventAttendeeResponse, String> allThree(Map<EventAttendeeResponse, String> links) {
    links.put(EventAttendeeResponse.ACCEPTED, ACCEPT_URL);
    links.put(EventAttendeeResponse.TENTATIVE, TENTATIVE_URL);
    links.put(EventAttendeeResponse.DECLINED, DECLINE_URL);
    return links;
  }

  /**
   * Renders a description carrying the given answer links.
   *
   * @param links the answer links, null for none
   * @return the plain text description
   */
  private String describe(Map<EventAttendeeResponse, String> links) {
    return EventIcsBuilder.description(Locale.ENGLISH, "Ada Lovelace", "Chemistry", null, null, links, null);
  }

  /**
   * All three answers reach the description.
   */
  @Test
  public void theDescriptionCarriesTheThreeAnswerLinks() {
    String description = describe(allThree(new LinkedHashMap<>()));

    assertTrue("accept link is offered: " + description, description.contains(ACCEPT_URL));
    assertTrue("tentative link is offered: " + description, description.contains(TENTATIVE_URL));
    assertTrue("decline link is offered: " + description, description.contains(DECLINE_URL));
  }

  /**
   * The order is the builder's, never the map's.
   *
   * <p>
   * <b>This is the churn guard.</b> The mirror compares DESCRIPTION byte for
   * byte, so a block whose order followed the iteration order of whatever map a
   * caller happened to pass would rewrite every copy whenever that order
   * changed. Three map implementations with three different natural orders must
   * all render identically.
   */
  @Test
  public void theOrderOfTheAnswersIsFixedAndNotTheMapS() {
    String fromLinked = describe(allThree(new LinkedHashMap<>()));
    String fromHash = describe(allThree(new HashMap<>()));
    String fromTree = describe(allThree(new TreeMap<>()));

    assertEquals("a HashMap must render exactly like a LinkedHashMap", fromLinked, fromHash);
    assertEquals("and so must a TreeMap", fromLinked, fromTree);

    int accept = fromLinked.indexOf(ACCEPT_URL);
    int tentative = fromLinked.indexOf(TENTATIVE_URL);
    int decline = fromLinked.indexOf(DECLINE_URL);
    assertTrue("accept comes first, being the answer most people are looking for", accept < tentative);
    assertTrue("and decline comes last, so a mis-click is the hardest of the three", tentative < decline);
  }

  /**
   * Two renders of the same event for the same recipient are byte-identical.
   *
   * <p>
   * The property EXO-89753 rests on: the mirror rewrites any copy whose
   * description changed, so a description that differed between renders would
   * put every copy into permanent churn - the defect EXO-89716 was spent
   * removing.
   */
  @Test
  public void twoRendersOfTheSameCopyAreByteIdentical() {
    Map<EventAttendeeResponse, String> links = allThree(new LinkedHashMap<>());

    assertEquals("the description must be byte-stable across renders", describe(links), describe(links));
  }

  /**
   * A channel offering no answer links gets a description with no answer block,
   * and no dangling prompt.
   */
  @Test
  public void aChannelWithNoAnswerLinksGetsNoBlockAtAll() {
    String none = describe(null);
    String empty = describe(new LinkedHashMap<>());

    assertEquals("no links and an empty map must render the same", none, empty);
    assertFalse("no prompt introducing nothing: " + none, none.contains("agenda.rsvpPrompt"));
    assertFalse("and no stray answer label either: " + none, none.contains("agenda.accepted"));
  }

  /**
   * A map whose links could not be minted renders no block rather than a prompt
   * over nothing.
   *
   * <p>
   * Not a defensive case: no token is minted for an event carrying no date to
   * bound it by (EXO-89752), so a blank link is a real outcome.
   */
  @Test
  public void aBlankLinkIsNotOffered() {
    Map<EventAttendeeResponse, String> blanks = new LinkedHashMap<>();
    blanks.put(EventAttendeeResponse.ACCEPTED, "");
    blanks.put(EventAttendeeResponse.DECLINED, null);

    String description = describe(blanks);

    assertEquals("a map of unusable links renders like no map at all", describe(null), description);
  }

  /**
   * The answer itself is never written into the description.
   *
   * <p>
   * Decided and agreed: <b>actions in the description, state in PARTSTAT</b>.
   * A description stating an answer the user has just changed reads as the click
   * having failed and invites a second click, because their client only sees the
   * rewritten copy at its own refresh. This pins that the builder is given no
   * way to write one.
   */
  @Test
  public void theCurrentAnswerIsNeverStatedInTheDescription() {
    String description = describe(allThree(new LinkedHashMap<>()));

    assertFalse("no PARTSTAT value leaks into the prose: " + description, description.contains("NEEDS-ACTION"));
    assertFalse("nor a bare answer constant: " + description, description.contains("ACCEPTED\n"));
  }
}

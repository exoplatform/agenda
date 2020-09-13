<template>
  <exo-drawer
    ref="customRecurrentEventDrawer"
    right
    body-classes="hide-scroll"
    class="customRecurrentEventDrawer"
    @closed="$emit('cancel')">
    <template slot="title">
      {{ $t('agenda.title.addEventRecurrent') }}
    </template>
    <template slot="content">
      <form
        ref="form"
        class="ma-5">
        <v-row class="flex d-flex flex-row">
          <v-col sm="4" class="d-flex">
            <label class="float-left text-subtitle-1 d-none d-md-inline my-auto">
              {{ $t('agenda.label.repeatEvery') }}
            </label>
          </v-col>
          <v-col sm="3" class="d-flex">
            <input
              v-model="eventRecurrence.interval"
              type="Number"
              name="recurrenceInterval"
              class="recurrenceInterval ignore-vuetify-classes my-auto recurrence-interval"
              autofocus
              required>
          </v-col>
          <v-col sm="4" class="d-flex">
            <select v-model="eventRecurrence.frequency" class="flex-end ignore-vuetify-classes width-auto my-auto">
              <option value="DAILY">{{ $t('agenda.day') }}</option>
              <option value="WEEKLY">{{ $t('agenda.week') }}</option>
              <option value="MONTHLY">{{ $t('agenda.month') }}</option>
              <option value="YEARLY">{{ $t('agenda.year') }}</option>
            </select>
          </v-col>
        </v-row>

        <div v-if="eventRecurrence.frequency === 'WEEKLY'" class="d-flex flex-column recurrenceDays mt-4">
          <label class="float-left text-subtitle-1 d-none d-md-inline">
            {{ $t('agenda.label.repeatOn') }}
          </label>
          <v-chip-group
            v-model="eventRecurrence.byDay"
            :show-arrows="false"
            next-icon=""
            prev-icon=""
            active-class="primary white--text"
            class="mx-auto"
            multiple>
            <v-chip
              v-for="day in days"
              :key="day.value"
              :value="day.value"
              class="mr-1">
              <span class="text-uppercase">
                {{ day.text }}
              </span>
            </v-chip>
          </v-chip-group>
        </div>

        <div class="control-group">
          <div class="d-flex flex-column mt-3">
            <div class="control-label text-subtitle-1">{{ $t('agenda.label.endRepeat') }}:</div>
            <div class="controls ml-5">
              <v-radio-group v-model="recurrentEventDate">
                <v-radio :label="$t('agenda.label.never')" value="never" />
                <v-radio value="count">
                  <template slot="label">
                    <v-row>
                      <v-col class="flex-grow-0">
                        {{ $t('agenda.label.after') }}
                      </v-col>
                      <v-col sm="3">
                        <input
                          v-model="eventRecurrence.count"
                          :disabled="recurrentEventDate !== 'count'"
                          type="Number"
                          class="recurrenceCount ignore-vuetify-classes">
                      </v-col>
                      <v-col class="pl-0">
                        {{ $t('agenda.label.events') }}
                      </v-col>
                    </v-row>
                  </template>
                </v-radio>
                <v-radio value="date">
                  <template slot="label">
                    <v-row>
                      <v-col class="flex-grow-0">
                        {{ $t('agenda.label.untilDate') }}
                      </v-col>
                      <v-col sm="8">
                        <date-picker
                          v-model="untilDate"
                          :disabled="recurrentEventDate !== 'date'" />
                      </v-col>
                    </v-row>
                  </template>
                </v-radio>
              </v-radio-group>
            </div>
          </div>
        </div>
      </form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn ml-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          class="btn btn-primary ml-2"
          @click="apply">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data() {
    return {
      eventRecurrence: {},
      recurrentEventDate: 'never',
      untilDate: null,
    };
  },
  computed: {
    days() {
      return [{
        text: this.getWeekDayLabel(6),
        value: 'SU'
      },{
        text: this.getWeekDayLabel(0),
        value: 'MO'
      },{
        text: this.getWeekDayLabel(1),
        value: 'TH'
      },{
        text: this.getWeekDayLabel(2),
        value: 'WE'
      },{
        text: this.getWeekDayLabel(3),
        value: 'TU'
      },{
        text: this.getWeekDayLabel(4),
        value: 'FR'
      },{
        text: this.getWeekDayLabel(5),
        value: 'SA'
      }];
    },
  },
  methods: {
    getWeekDayLabel(weekDayNumber) {
      const date = new Date();
      date.setDate(weekDayNumber);
      const dayName = date.toLocaleDateString(eXo.env.portal.language, { weekday: 'short' });
      return dayName.length > 3 ? dayName.substring(0, 3) : dayName;
    },
    apply() {
      if (this.recurrentEventDate === 'date') {
        this.eventRecurrence.until = this.$agendaUtils.toRFC3339(this.untilDate);
        this.eventRecurrence.count = null;
      } else if (this.recurrentEventDate === 'count') {
        this.eventRecurrence.until = null;
      } else {
        this.eventRecurrence.until = null;
        this.eventRecurrence.count = null;
      }
      this.$emit('apply', this.eventRecurrence);
      this.$refs.customRecurrentEventDrawer.close();
    },
    close() {
      this.$refs.customRecurrentEventDrawer.close();
    },
    open(eventRecurrence) {
      this.eventRecurrence = eventRecurrence || {};
      if (this.eventRecurrence.until) {
        this.recurrentEventDate = 'date';
        this.untilDate = new Date(this.eventRecurrence.until);
        this.eventRecurrence.count = null;
      } else if (this.eventRecurrence.count) {
        this.recurrentEventDate = 'count';
        this.eventRecurrence.until = null;
      } else {
        this.recurrentEventDate = 'never';
        this.eventRecurrence.until = null;
        this.eventRecurrence.count = null;
      }
      this.$refs.customRecurrentEventDrawer.open();
    },
  }
};
</script>

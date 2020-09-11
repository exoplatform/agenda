<template>
  <exo-drawer
    ref="customRecurrentEventDrawer"
    right
    body-classes="hide-scroll"
    class="customRecurrentEventDrawer">
    <template slot="title">
      {{ $t('agenda.drawer.title.addEventRecurrent') }}
    </template>
    <template slot="content">
      <form
        v-if="event"
        ref="form"
        class="ma-5">
        <div class="d-flex flex-row">
          <label class="float-left text-subtitle-1 d-none d-md-inline mr-2 mt-3">
            {{ $t('agenda.drawer.label.repeat') }}
          </label>
          <select class="ignore-vuetify-classes my-3">
            <option value="NO REPEAT">{{ $t('agenda.doNotRepeat') }}</option>
            <option value="DAILY">{{ $t('agenda.daily') }}</option>
            <option value="WEEKLY">{{ $t('agenda.weekly',{ 0 : dayNamefromDate }) }}</option>
            <option value="MONTHLY">{{ $t('agenda.monthly',{ 0 : dayNamefromDate }) }}</option>
            <option value="YEARLY">{{ $t('agenda.annually',{ 0 : monthFromDate, 1: dayNumberFromDate}) }}</option>
            <option value="EVERY WEEKDAY">{{ $t('agenda.everyWeekDay') }}</option>
          </select>
        </div>

        <div class="d-flex flex-row">
          <label class="float-left text-subtitle-1 d-none d-md-inline mr-2 mt-3">
            {{ $t('agenda.drawer.label.repeatEvery') }}
          </label>
          <input
            type="Number"
            name="endRepeat"
            class="endRepeat input-block-level ignore-vuetify-classes my-3"
            maxlength="200"
            autofocus
            required>
        </div>

        <div class="d-flex flex-row">
          <label class="float-left text-subtitle-1 d-none d-md-inline mr-2 mt-3">
            {{ $t('agenda.drawer.label.repeatOn') }}
          </label>
          <v-chip-group
            v-model="selection"
            active-class="deep-purple--text text--accent-4"
            class="my-3"
            multiple>
            <v-chip
              v-for="day in days"
              :key="day"
              :value="day">
              {{ day }}
            </v-chip>
          </v-chip-group>
        </div>

        <div class="control-group">
          <div class="d-flex flex-row">
            <div class="control-label">{{ $t('agenda.drawer.label.endRepeat') }}:</div>
            <div class="controls ml-5">
              <div class="radioBoxArea">
                <label class="uiRadio">
                  <input
                    id="endNever"
                    type="radio"
                    value="neverEnd"
                    class="endRepeat ignore-vuetify-classes"
                    name="endRepeat">
                  <span>{{ $t('agenda.drawer.label.never') }}</span>
                </label>
              </div>
              <div class="radioBoxArea">
                <label class="uiRadio">
                  <input
                    id="endAfter"
                    type="radio"
                    value="endAfter"
                    class="ignore-vuetify-classes my-3"
                    name="endRepeat">
                  <span>{{ $t('agenda.drawer.label.after') }}</span>
                </label>
                <input
                  id="endAfterNumber"
                  name="endAfterNumber"
                  class="endAfterNumber ignore-vuetify-classes my-3"
                  type="number"
                  min="1"
                  step="1">
                {{ $t('agenda.drawer.label.occurrence') }}
              </div>
              <div id="endByDateContainer" class="d-flex flex-row">
                <label class="uiRadio pull-left">
                  <input
                    id="endByDate"
                    type="radio"
                    value="endByDate"
                    name="endRepeat"
                    class="ignore-vuetify-classes my-3">
                  <span>{{ $t('agenda.drawer.label.byDate') }}</span>
                </label>
                <input
                  lang="en"
                  type="date"
                  class="ignore-vuetify-classes my-3"
                  name="endDate">
              </div>
            </div>
          </div>
        </div>
      </form>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      never: false,
      after: false,
      byDay: false,
      selection: ['Mo'],
      days:['Su','Mo','Tu','We','Th','Fr','Sa']
    };
  },
  computed: {
    dayNamefromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getDayNameFromDate(day);
    },
    monthFromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getMonthFromDate(day);
    },
    dayNumberFromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getDayNumberFromDate(day);
    },
  },
  methods: {
    open() {
      this.$refs.customRecurrentEventDrawer.open();
      console.error('********',this.selection);
    },
  }
};
</script>

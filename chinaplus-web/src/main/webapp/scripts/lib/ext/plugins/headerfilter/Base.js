/**
 * Abstract base class for filter implementations.
 */
Ext.define('Ext.grid.headerfilters.filter.Base', {
    mixins: [
        'Ext.mixin.Factoryable'
    ],

    factoryConfig: {
        type: 'grid.headerfilter'
    },

    $configPrefixed: false,
    $configStrict: false,
    
    itemNumber: undefined,

    config: {
        /**
         * @cfg {Object} [itemDefaults]
         * The default configuration options for any menu items created by this filter.
         *
         * Example usage:
         *
         *      itemDefaults: {
         *          width: 150
         *      },
         */
        itemDefaults: null,

        menuDefaults: {
            /*xtype: 'container',
            border:false,
            resizable:true,
            items:[]*/
        }
    },
    
    allSingleFilter: true,

    /**
     * @property {Boolean} active
     * True if this filter is active.  Use setActive() to alter after configuration.
     */
    /**
     * @cfg {Boolean} active
     * Indicates the initial status of the filter (defaults to false).
     */
    active: false,

    /**
     * @property {String} type
     * The filter type. Used by the filters.Feature class when adding filters and applying state.
     */
    type: 'string',

    /**
     * @cfg {String} dataIndex
     * The {@link Ext.data.Store} dataIndex of the field this filter represents.
     * The dataIndex does not actually have to exist in the store.
     */
    dataIndex: null,

    /**
     * @property {Ext.menu.Menu} menu
     * The filter configuration menu that will be installed into the filter submenu of a column menu.
     */
    menu: null,

    /**
     * @cfg {Number} updateBuffer
     * Number of milliseconds to wait after user interaction to fire an update. Only supported
     * by filters: 'list', 'numeric', and 'string'.
     */
    updateBuffer: 500,

    isGridFilter: true,

    defaultRoot: 'data',

    /**
     * The prefix for id's used to track stateful Store filters.
     * @private
     */
    filterIdPrefix: 'x-gridfilter',

    /**
     * @event activate
     * Fires when an inactive filter becomes active
     * @param {Ext.grid.filters.filter.Filter} this
     */

    /**
     * @event deactivate
     * Fires when an active filter becomes inactive
     * @param {Ext.grid.filters.filter.Filter} this
     */

    /**
     * @event update
     * Fires when a filter configuration has changed
     * @param {Ext.grid.filters.filter.Filter} this The filter object.
     */

    /**
     * Initializes the filter given its configuration.
     * @param {Object} config
     */
    constructor: function (config) {
        var me = this;
        //console.log('grid.headerfilter!!');

        me.initConfig(config);

        me.store = me.grid.store;
    },

    /**
     * Destroys this filter by purging any event listeners, and removing any menus.
     */
    destroy: function(){
        if (this.menu){
            this.menu.destroy();
        }
    },

    addStoreFilter: function (filter) {
        this.store.getFilters().add(filter);
    },

    createFilter: function (config, key) {
        config.id = this.getBaseIdPrefix();

        if (!config.property) {
            config.property = this.column.dataIndex;
        }

        if (!config.root) {
            config.root = this.defaultRoot;
        }

        if (key) {
            config.id += '-' + key;
        }

        return new Ext.util.Filter(config);
    },
    
    /**
     * @private
     * Creates the Menu for this filter.
     * @param {Object} config Filter configuration
     * @return {Ext.menu.Menu}
     */
    createMenu: function () {
        //var config = this.getMenuConfig();
        //this.menu = Ext.widget(config);
    },

    getBaseIdPrefix: function () {
        return this.filterIdPrefix + '-' + this.column.dataIndex;
    },

    getMenuConfig: function () {
        var menuConfig = Ext.apply({}, this.getMenuDefaults());
        for(var i=0;i<this.itemNumber;i++){
        	menuConfig.items.push(this.itemDefaults);
        }
        //console.log(menuConfig);
        return menuConfig;
    },
    
    getStoreFilter: function (key) {
        var id = this.getBaseIdPrefix();

        if (key) {
            id += '-' + key;
        }

        return this.store.getFilters().get(id);
    },

    removeStoreFilter: function (filter) {
        this.store.getFilters().remove(filter);
    },

    /**
     * @private
     * @method getValue
     * Template method to be implemented by all subclasses that is to
     * get and return the value of the filter.
     * @return {Object} The 'serialized' form of this filter
     * @template
     */

    /**
     * @private
     * @method setValue
     * Template method to be implemented by all subclasses that is to
     * set the value of the filter and fire the 'update' event.
     * @param {Object} data The value to set the filter
     * @template
     */

    /**
     * @method
     * Template method to be implemented by all subclasses that is to
     * return true if the filter has enough configuration information to be activated.
     * Defaults to always returning true.
     * @return {Boolean}
     */

    /**
     * Sets the status of the filter and fires the appropriate events.
     * @param {Boolean} active The new filter state.
     * @param {String} key The filter key for columns that support multiple filters.
     */
    setActive: function (active) {
        var me = this,
            owner = me.owner,
            menuItem = owner.activeFilterMenuItem,
            filterCollection;
            
        //console.log(me.active !== active);
        //console.dir(me);

        if (me.active !== active) {
            me.active = active;

            filterCollection = me.store.getFilters();
            filterCollection.beginUpdate();

            if (active) {
                me.activate();
            } else {
                me.deactivate();
            }

            filterCollection.endUpdate();

            // Make sure we update the 'Filters' menu item.
            if (menuItem && menuItem.activeFilter === me) {
                menuItem.setChecked(active);
            }

            me.column[active ? 'addCls' : 'removeCls'](owner.filterCls);

            // TODO: fire activate/deactivate
        }
    },

    showMenu: function (menuItem) {
        if (!this.menu) {
            this.createMenu();
        }

        menuItem.activeFilter = this;

        menuItem.setMenu(this.menu, false);
        menuItem.setChecked(this.active);
        // Disable the menu if filter.disabled explicitly set to true.
        menuItem.setDisabled(this.disabled === true);

        if (this.active) {
            this.activate(/*showingMenu*/ true);
        }
    },
    
    showPanel: function (column){
    	//console.log('should entering');
    	if (!this.menu) {
            this.createMenu();
        }/*
        var items = this.menu.items;
        items.each(function(record){
        	//console.log(record.config);
        	column.items.add(record);
        });*/
    	
    	var items = column.items;
    	//console.dir(this.inputItem);
    	//items.items[0].add(this.inputItem);
    },

    updateStoreFilter: function (filter) {
        this.store.getFilters().notify('endupdate');
    }
});

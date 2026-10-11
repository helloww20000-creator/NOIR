(function(){
  if (!window.NoirNativeBLE || navigator.bluetooth) return;
  const pending = new Map(); let seq=0; const listeners={};
  window.__noirBleResolve=(id,value)=>{const p=pending.get(id);if(!p)return;pending.delete(id);value&&value.error?p.reject(new Error(value.error)):p.resolve(value)};
  window.__noirBleEvent=(name,detail)=>{(listeners[name]||[]).forEach(fn=>fn(detail||{}));};
  function call(method,...args){return new Promise((resolve,reject)=>{const id=String(++seq);pending.set(id,{resolve,reject});try{window.NoirNativeBLE[method](id,...args)}catch(e){pending.delete(id);reject(e)}})}
  function on(name,fn){(listeners[name]||(listeners[name]=[])).push(fn)}
  const norm=s=>String(s).toLowerCase().replace(/^0x/,'');
  function full(s){if(typeof s==='number')s=s.toString(16);s=norm(s);return s.length===4?'0000'+s+'-0000-1000-8000-00805f9b34fb':s}
  class Characteristic extends EventTarget{
    constructor(service,uuid,props){super();this.service=service;this.uuid=full(uuid);this.properties={read:!!(props&2),write:!!(props&8),writeWithoutResponse:!!(props&4),notify:!!(props&16),indicate:!!(props&32)};this.value=null;this._lastRead=null;
      on('notify',d=>{if(full(d.uuid)===this.uuid){this.value=hexBuffer(d.hex);this.dispatchEvent(new Event('characteristicvaluechanged'))}});
      on('read',d=>{if(full(d.uuid)===this.uuid){this.value=hexBuffer(d.hex);this._lastRead&&this._lastRead(this.value);this._lastRead=null}});
    }
    async readValue(){return new Promise(async(resolve,reject)=>{this._lastRead=resolve;try{const r=await call('read',this.service.uuid,this.uuid);if(r.error){this._lastRead=null;reject(new Error(r.error))}else if(r.pending){setTimeout(()=>{if(this._lastRead){this._lastRead=null;reject(new Error('GATT read timed out'))}},6000)}}catch(e){this._lastRead=null;reject(e)}})}
    async writeValueWithoutResponse(value){return this._write(value,true)}
    async writeValueWithResponse(value){return this._write(value,false)}
    async writeValue(value){return this._write(value,false)}
    async _write(value,without){const bytes=new Uint8Array(value.buffer||value,value.byteOffset||0,value.byteLength||value.length);const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');await call('write',this.service.uuid,this.uuid,hex,without)}
    async startNotifications(){await call('notifications',this.service.uuid,this.uuid,true);return this}
    async stopNotifications(){await call('notifications',this.service.uuid,this.uuid,false);return this}
  }
  function hexBuffer(hex){const a=(hex.match(/../g)||[]).map(x=>parseInt(x,16));return new DataView(new Uint8Array(a).buffer)}
  class Service{
    constructor(device,uuid,chars){this.device=device;this.uuid=full(uuid);this._chars=chars||[]}
    async getCharacteristic(uuid){const target=full(uuid);const c=this._chars.find(x=>full(x.uuid)===target);if(!c)throw new Error('Characteristic not found: '+uuid);return new Characteristic(this,c.uuid,c.properties)}
  }
  class Server{
    constructor(device){this.device=device;this.connected=true;this._services=null}
    async getPrimaryService(uuid){if(!this._services){const r=await call('listServices');this._services=r.services||[]}const s=this._services.find(x=>full(x.uuid)===full(uuid));if(!s)throw new Error('Service not found: '+uuid);return new Service(this.device,s.uuid,s.characteristics)}
    async getPrimaryServices(){if(!this._services){const r=await call('listServices');this._services=r.services||[]}return this._services.map(s=>new Service(this.device,s.uuid,s.characteristics))}
    disconnect(){window.NoirNativeBLE.disconnect();this.connected=false;this.device.dispatchEvent(new Event('gattserverdisconnected'))}
  }
  class Device extends EventTarget{
    constructor(info){super();this.id=info.id;this.name=info.name;this.gatt=new Server(this)}
  }
  Object.defineProperty(navigator,'bluetooth',{configurable:true,value:{
    async requestDevice(options){const r=await call('scan');const devices=r.devices||[];if(!devices.length)throw new Error('No BLE devices found. Keep the watch awake and try again.');let chosen;
      if(options&&options.filters&&options.filters.length){chosen=devices.find(d=>options.filters.some(f=>!f.name&&!f.namePrefix&&!f.services||f.name===d.name||f.namePrefix&&d.name.startsWith(f.namePrefix)))}
      if(!chosen){const menu=devices.map((d,i)=>i+': '+d.name+' ('+d.rssi+' dBm)').join('\n');const answer=prompt('Select your watch by number:\n'+menu,'0');if(answer===null)throw new Error('Device selection cancelled');chosen=devices[Number(answer)]}
      if(!chosen)throw new Error('Invalid device selection');const connected=await call('connect',chosen.id);return new Device(connected)}
  }});
  window.__NOIR_NATIVE_BLE__=true;
})();
local ID, SCAFFOLD = "scaffoldlua", "ScaffoldWalk"
local REACH = 4.5

local FACE_MARGIN = 0.02
local RAY_EPSILON = 0.00002
local RAY_LIMIT = 2048
local FACES = {
    {id=1, name="UP",    x=0,  y=1, z=0},
    {id=2, name="NORTH", x=0,  y=0, z=-1},
    {id=3, name="SOUTH", x=0,  y=0, z=1},
    {id=4, name="WEST",  x=-1, y=0, z=0},
    {id=5, name="EAST",  x=1,  y=0, z=0}
}
local SEARCH_OFFSETS={}
for x=-4,5 do for y=-4,-1 do for z=-4,5 do
    SEARCH_OFFSETS[#SEARCH_OFFSETS+1]={x=x,y=y,z=z,distance=x*x+y*y+z*z}
end end end
table.sort(SEARCH_OFFSETS,function(a,b)
    if a.distance~=b.distance then return a.distance<b.distance end
    if a.y~=b.y then return a.y>b.y end
    if a.x~=b.x then return a.x<b.x end
    return a.z<b.z
end)

local nativeLease,slotLease,state=nil,nil,{}
local placeBeforeMove,restoreFreeze,restoreSneak
local utils={frame={},math={},inventory={}}
local function finite(v)
    return type(v)=="number" and v==v and v~=math.huge and v~=-math.huge
end
local function clamp(v,a,b) return math.max(a,math.min(b,v)) end
local function sign(v) return v>0 and 1 or (v<0 and -1 or 0) end
local function wrap(v) return (v+180)%360-180 end
local function near(v,previous) return previous+wrap(v-previous) end
local function read(fn,fallback,...)
    if type(fn)~="function" then return fallback end
    local ok,v=pcall(fn,...)
    if ok and v~=nil then return v end
    return fallback
end
local function setting(key,default) return read(modules.get_setting,default,ID,key) end
local function nativeEnabled() return false end
local function eyeHeight(sneaking)

    return sneaking and 1.5399999618530273 or 1.6200000047683716
end
local function phase(event)
    if type(event)=="boolean" then return event end
    if event==nil then return nil end
    local value=read(function() return event.pre end,nil)
    if value==true or value==1 or value=="true" then return true end
    if value==false or value==0 or value=="false" then return false end
    return nil
end
local function reset()
    state={owner=nil,tick=-1,frame=0,preTick=-1,airTicks=0,groundTicks=0,counterTick=-1,
        serverYaw=nil,serverPitch=nil,serverPos=nil,lastLookRef=nil,lastPositionRef=nil,
        yaw=nil,pitch=nil,rotYaw=nil,rotPitch=nil,lock=false,clutchTick=0,
        blocks={},faceCache={},searchSupport=nil,cacheTick=-1,rays=0,
        blockSlots={},slotTick=-100,plan=nil,movementSerial=0,lastAttemptTick=-100,
        attemptCount=0,lastPlaceSerial=-1,cooldownUntil=-100,baseY=nil,
        pending={},rejected={},sending=nil,aimTarget=nil,poseValid=false,
        lastMovementTick=-1,fastMath=false,mathKnown=false,sentSneaking=nil,
        freeze=nil,sneakLease=nil,jumpedGround=false,attackTick=-100,
        prepareBlocked=nil,
        inventoryReason=nil,mainInventoryTick=-100,mainInventoryBlocks=false,
        stage="idle",lastSlotRef=nil,manualJumpActive=false,
        previousGround=nil,placementPrimed=false,
        syncedSlot=nil,publishedSlot=nil,preparedSlot=nil,lastMovementRef=nil,inputDamping=nil,
        lowStackSwitched={}}
end
function utils.frame.begin()

    state.frame=state.frame+1
    state.preTick,state.counterTick=-1,-1
end
local function restoreSlot()
    if slotLease then
        if read(player.get_id,nil)==slotLease.owner
            and read(player.get_held_item_slot,nil)==slotLease.applied then
            pcall(player.set_held_item,slotLease.original)
        end
        slotLease=nil
    end
end
local function releaseNative()
    if nativeLease then
        if nativeLease.original then
            pcall(modules.set_state,SCAFFOLD,true)
        end
        if nativeLease.sprintOriginal then
            pcall(modules.set_state,"Sprint",true)
        end
        if nativeLease.auraOriginal then pcall(modules.set_state,"KillAura",true) end
        if nativeLease.autoBlockOriginal then pcall(modules.set_state,"exhibitionautoblock",true) end
        nativeLease=nil
    end
end
local function suspend(restoreSelection)
    if restoreFreeze then restoreFreeze() end
    if restoreSneak then restoreSneak() end

    if restoreSelection then restoreSlot() end
    state.plan=nil
    state.poseValid=false
    state.placementPrimed=false
    state.plannedTick=-1
    state.blocks={}
end
local function stop()
    suspend(true)
    releaseNative()
    reset()
end
local function claimNative()
    nativeLease={original=nativeEnabled(),sprintOriginal=read(modules.is_enabled,false,"Sprint"),
        auraOriginal=read(modules.is_enabled,false,"KillAura"),
        autoBlockOriginal=read(modules.is_enabled,false,"exhibitionautoblock"),
        deferSprint=read(modules.is_enabled,false,"pharosautoknockback") or read(modules.is_enabled,false,"bedauralua"),
        acquiring=true,requestedFrame=state.frame,retried=false}
    if nativeLease.autoBlockOriginal then

        nativeLease.deferAura=true;nativeLease.auraOriginal=nil
        pcall(modules.set_state,"exhibitionautoblock",false)
    end
    if nativeLease.original then
        local ok=pcall(modules.set_state,SCAFFOLD,false)
        if not ok then
            nativeLease.error="Native Scaffold state request failed"
            return false
        end
    end
    if nativeLease.deferSprint then nativeLease.sprintOriginal=nil end
    if nativeLease.sprintOriginal then
        local ok=pcall(modules.set_state,"Sprint",false)
        if not ok then
            nativeLease.error="Sprint state request failed"
            return false
        end
    end
    if nativeLease.auraOriginal then
        local ok=pcall(modules.set_state,"KillAura",false)
        if not ok then nativeLease.error="KillAura state request failed";return false end
    end
    return true
end
local function acquireNativeState()
    if not nativeLease or not nativeLease.acquiring then return true end
    if nativeLease.error then return false,nativeLease.error end
    if nativeLease.deferAura then
        if read(modules.is_enabled,false,"exhibitionautoblock") then return false,"Waiting for AutoBlock to release Aura" end
        if state.frame-nativeLease.requestedFrame<2 then return false,"Waiting for AutoBlock Aura restoration" end
        nativeLease.deferAura=false
        nativeLease.auraOriginal=read(modules.is_enabled,false,"KillAura")
        if nativeLease.auraOriginal then pcall(modules.set_state,"KillAura",false) end
    end
    if nativeLease.deferSprint then
        if state.frame-nativeLease.requestedFrame<2 then return false,"Waiting for the previous Lua Sprint owner to release" end
        nativeLease.deferSprint=false
        nativeLease.sprintOriginal=read(modules.is_enabled,false,"Sprint")
        nativeLease.requestedFrame=state.frame
        if nativeLease.sprintOriginal then pcall(modules.set_state,"Sprint",false) end
    end
    local scaffoldOn=nativeEnabled()
    local sprintOn=read(modules.is_enabled,false,"Sprint")
    local auraOn=nativeLease.auraOriginal and read(modules.is_enabled,false,"KillAura")
    if not scaffoldOn and not sprintOn and not auraOn then nativeLease.acquiring=false;return true end
    local elapsed=state.frame-nativeLease.requestedFrame

    if elapsed>=2 and not nativeLease.retried then
        nativeLease.retried=true
        if scaffoldOn then pcall(modules.set_state,SCAFFOLD,false) end
        if sprintOn then pcall(modules.set_state,"Sprint",false) end
        if auraOn then pcall(modules.set_state,"KillAura",false) end
    end
    if elapsed>=4 then return false,"Native Scaffold / Sprint remains active after state requests" end
    return false,"Waiting for native Scaffold / Sprint state requests"
end
local function context()
    local owner=read(player.get_id,nil)
    local tick=read(player.get_ticks_existed,nil)
    if not finite(owner) or owner<0 or not finite(tick) or tick<0
        or read(player.is_dead,true) then suspend(true); reset(); return nil end
    if (state.owner~=nil and state.owner~=owner) or tick<state.tick then
        suspend(true); reset()
    end
    state.owner,state.tick=owner,tick
    if state.syncedSlot==nil and state.preparedSlot==nil and state.lastSlotRef==nil then
        state.syncedSlot=read(player.get_held_item_slot,nil)
    end
    if state.cacheTick~=tick then state.blocks={}; state.cacheTick=tick end
    local x,y,z=read(player.get_position_x,nil),read(player.get_position_y,nil),read(player.get_position_z,nil)
    local yaw=read(player.get_yaw,nil)
    if not finite(x) or not finite(y) or not finite(z) or not finite(yaw) then suspend(); return nil end
    return {x=x,y=y,z=z,eye=y+eyeHeight(read(player.is_sneaking,false)),yaw=yaw,tick=tick}
end

local function block(x,y,z)
    x,y,z=math.floor(x),math.floor(y),math.floor(z)
    local key=x..","..y..","..z
    local cached=state.blocks[key]
    if cached~=nil then return cached~=false and cached or nil end
    local b=read(world.get_block_at,nil,x,y,z)
    state.blocks[key]=b or false
    return b
end
local function replaceable(b)
    if b==nil or type(b.name)~="string" then return false end
    local name=string.lower(b.name)
    return name=="tile.air" or name=="air" or name=="minecraft:air"
        or name=="tile.tallgrass" or name=="tile.deadbush"
        or name=="tile.snow" or name=="tile.vine" or name=="tile.fire"
        or name=="tile.water" or name=="tile.lava"
end
local function support(b)
    if b==nil or replaceable(b) or type(b.name)~="string" then return false end
    local name=string.lower(b.name)
    local cubeGlass=name=="tile.glass" or name=="tile.stainedglass" or name=="minecraft:glass"
    if b.full_block~=true and not cubeGlass then return false end

    if name:find("chest",1,true) or name:find("furnace",1,true)
        or name:find("hopper",1,true) or name:find("workbench",1,true)
        or name:find("dispenser",1,true) or name:find("dropper",1,true)
        or name:find("enchant",1,true) or name:find("anvil",1,true)
        or name:find("brewing",1,true) then return false end
    return true
end
local function supportAt(x,y,z)
    local p=state.pending[x..","..y..","..z]
    if (state.rejected[x..","..y..","..z] or -1)>state.tick then return false end

    if p~=nil then
        local look=p.followingLook
        if not p.seen or not p.packet or p.packet.cancel==true
            or not p.followingRef or p.followingRef.cancel==true or not look or look.cancel==true
            or not finite(look.yaw) or not finite(look.pitch)
            or math.abs(wrap(look.yaw-p.yaw))>0.001 or math.abs(look.pitch-p.pitch)>0.001 then return false end
    end
    return support(block(x,y,z))
end
local function cellKey(x,y,z) return x..","..y..","..z end
local function rejected(t)
    local key=cellKey(t.x+t.face.x,t.y+t.face.y,t.z+t.face.z)
    local pending=state.pending[key]
    return (state.rejected[key] or -1)>state.tick or (pending~=nil and not pending.seen)
end
local function watchPlacements(c)
    local invalid={}
    for key,p in pairs(state.pending) do
        local b=read(world.get_block_at,nil,p.x,p.y,p.z)
        local present=b~=nil and not replaceable(b)
        local cancelled=p.packet and read(function() return p.packet.cancel end,false)==true
        local reason
        if cancelled then reason="Own C08 cancelled after its hook"
        elseif p.followingRef and (p.followingRef.cancel==true or not p.followingLook
            or p.followingLook.cancel==true or not finite(p.followingLook.yaw)
            or not finite(p.followingLook.pitch) or math.abs(wrap(p.followingLook.yaw-p.yaw))>0.001
            or math.abs(p.followingLook.pitch-p.pitch)>0.001) then reason="Placement's following movement look changed / cancelled"
        elseif p.seen and b~=nil and not present then reason="Local block disappeared after placement"
        elseif not p.seen and c.tick-p.tick>=3 then reason="Observed C08 without a local block"
        elseif not p.followingRef and c.tick-p.tick>=3 then reason="Placement look was not published in the following movement" end
        if reason then
            state.rejected[key]=c.tick+40
            state.cooldownUntil=math.max(state.cooldownUntil,c.tick+5)
            state.pending[key]=nil
            invalid[key]=true
        elseif c.tick-p.tick>=40 then
            state.pending[key]=nil
        elseif present then p.seen=true end
    end

    local changed=true
    while changed do
        changed=false
        for key,p in pairs(state.pending) do
            if p.supportKey and invalid[p.supportKey] then
                state.pending[key]=nil;state.rejected[key]=c.tick+40
                invalid[key]=true;changed=true
            end
        end
    end
    if next(invalid)~=nil then state.aimTarget=nil;state.blocks={} end
    for key,tick in pairs(state.rejected) do if tick<=c.tick then state.rejected[key]=nil end end
end
local function blockItem(item)
    if item==nil then return false,"empty" end
    local rawName=read(function() return item.name end,nil)
    if type(rawName)~="string" then return false,"name unavailable" end

    local name=string.lower(rawName)
    if name:sub(1,5)~="tile." then return false,"not a tile item" end
    if name=="tile.air" or name=="tile.water" or name=="tile.lava" or name=="tile.fire"
        or name=="tile.sand" or name=="tile.gravel" or name=="tile.snow"
        or name=="tile.vine" or name=="tile.cake" or name=="tile.pistonbase"
        or name=="tile.pistonstickybase" or name=="tile.soil" or name=="tile.daylightdetector"
        or name=="tile.cocoa" or name=="tile.dragonegg" or name=="tile.waterlily"
        or name=="tile.reeds" or name=="tile.soulsand" or name=="tile.cauldron"
        or name=="tile.portal" or name=="tile.endportalframe" then return false,"excluded block" end
    for _,word in ipairs({"slime","web","cactus","workbench","noteblock","jukebox",
        "leaves","sapling","bush","flower","tallgrass","doubleplant","slab","stairs",
        "wall","fence","door","bars","pane","thinglass","carpet","tnt","diode",
        "repeater","comparator","torch","button","lever","pressure","tripwire",
        "ladder","rail","sign","chest","furnace","hopper","dispenser",
        "dropper","anvil","enchant","brewing","skull","pot"}) do
        if name:find(word,6,true) then return false,"excluded block: "..word end
    end

    if name=="tile.bed" or name=="tile.redstonedust" or name=="tile.redstonewire" then
        return false,"excluded block"
    end
    return true
end
function utils.inventory.stack(slot)
    if type(inventory.get_item)~="function" then return nil,"get_item unavailable" end
    local ok,item=pcall(inventory.get_item,slot)
    if not ok then return nil,"get_item failed: "..tostring(item):sub(1,160) end
    if item==nil then return nil end
    local name=read(function() return item.name end,nil)
    local rawCount=read(function() return item.stack_size end,nil)
    local count=type(rawCount)=="number" and rawCount or
        (type(rawCount)=="string" and tonumber(rawCount) or nil)
    local allowed,why=blockItem(item)
    if allowed and (not finite(count) or count<=0) then
        allowed,why=false,finite(count) and "empty stack" or "stack_size unavailable"
    end
    return {item=item,name=name,count=count,rawCount=rawCount,
        solid=read(function() return item.solid end,nil),allowed=allowed,reason=why}
end
function utils.inventory.hotbar()
    local result={slots={},found=false,occupied=0,errors=0}
    for slot=0,8 do
        local stack,err=utils.inventory.stack(slot)
        if err then
            result.errors=result.errors+1
        elseif stack then
            result.occupied=result.occupied+1
            if stack.allowed then
                result.slots[slot]=stack
                result.found=true
            end
        end
    end
    return result
end
local function hasBlocks()
    local hotbar=utils.inventory.hotbar()
    state.blockSlots,state.slotTick={},state.tick
    for slot in pairs(hotbar.slots) do state.blockSlots[slot]=true end
    if hotbar.found then
        state.inventoryReason=nil
        state.mainInventoryTick,state.mainInventoryBlocks=-100,false
        return true
    end
    if hotbar.errors>0 then state.inventoryReason="Cannot read hotbar items"
    elseif hotbar.occupied>0 then state.inventoryReason="No eligible hotbar blocks"
    else state.inventoryReason="No hotbar blocks" end

    if state.tick-state.mainInventoryTick>=20 then
        state.mainInventoryTick,state.mainInventoryBlocks=state.tick,false
        for slot=9,35 do
            local stack=utils.inventory.stack(slot)
            if stack and stack.allowed then state.mainInventoryBlocks=true;break end
        end
    end
    if state.mainInventoryBlocks and hotbar.errors==0 then
        state.inventoryReason="Main-inventory blocks detected; move a stack to the hotbar"
    end
    return false
end

function utils.math.f32(value)
    if not finite(value) or value==0 then return value end
    local absolute=math.abs(value)
    local exponent
    if type(math.frexp)=="function" then
        local mantissa
        mantissa,exponent=math.frexp(absolute)
    else
        local scaled=absolute
        exponent=0
        while scaled>=1 do scaled=scaled/2;exponent=exponent+1 end
        while scaled<0.5 do scaled=scaled*2;exponent=exponent-1 end
    end
    local step=2^math.max(exponent-24,-149)
    local units=absolute/step
    local rounded=math.floor(units)
    local fraction=units-rounded
    if fraction>0.5 or (fraction==0.5 and rounded%2~=0) then rounded=rounded+1 end
    local result=rounded*step
    if result>=2^128 then result=math.huge end
    return value<0 and -result or result
end
local f32=utils.math.f32
local sineCache,fastSineCache,alignedSineCache={},{},{}
local function trunc(value) return value<0 and math.ceil(value) or math.floor(value) end
local function sineEntry(index,fast)
    local count=fast and 4096 or 65536
    index=index%count
    local cache=fast=="aligned" and alignedSineCache or (fast and fastSineCache or sineCache)
    if cache[index]==nil then
        local radians
        if fast=="aligned" then

            radians=index*math.pi*2/4096
        elseif fast then
            radians=f32(f32(f32(index+0.5)/4096)*f32(f32(math.pi)*2))
            for _,degrees in ipairs({0,90,180,270}) do
                if index==trunc(f32(degrees*f32(11.377778)))%4096 then
                    radians=f32(degrees*f32(0.017453292))
                end
            end
        else radians=index*math.pi*2/65536 end
        local value=math.sin(radians)
        if fast=="aligned" then value=math.floor(value*100000000+0.5)/100000000 end
        cache[index]=f32(value)
    end
    return cache[index]
end
local function sincos(yaw,fast,jump)
    local rad=jump and f32(f32(yaw)*f32(0.017453292)) or f32(f32(f32(yaw)*f32(math.pi))/180)
    if fast then
        local scaled=f32(rad*f32(651.8986))
        local cosine=fast=="aligned" and f32(scaled+1024) or
            f32(f32(rad+f32(f32(math.pi)/2))*f32(651.8986))
        return sineEntry(trunc(scaled),fast),sineEntry(trunc(cosine),fast)
    end
    local scaled=f32(rad*f32(10430.378))
    return sineEntry(trunc(scaled),false),sineEntry(trunc(f32(scaled+16384)),false)
end
local function lookVector(yaw,pitch,fast)

    local yr=f32(f32(-f32(yaw)*f32(0.017453292))-f32(math.pi))
    local pr=f32(-f32(pitch)*f32(0.017453292))
    local function sc(rad)
        if fast then
            local scaled=f32(rad*f32(651.8986))
            local cosine=fast=="aligned" and f32(scaled+1024) or
                f32(f32(rad+f32(f32(math.pi)/2))*f32(651.8986))
            return sineEntry(trunc(scaled),fast),sineEntry(trunc(cosine),fast)
        end
        local scaled=f32(rad*f32(10430.378))
        return sineEntry(trunc(scaled),false),sineEntry(trunc(f32(scaled+16384)),false)
    end
    local sy,cy=sc(yr)
    local sp,cp=sc(pr)
    return f32(sy*-cp),sp,f32(cy*-cp)
end

local function inputVector(yaw,forward,strafe,damping,acceleration,fast)
    forward,strafe=f32(f32(forward)*f32(damping)),f32(f32(strafe)*f32(damping))
    local squared=f32(f32(forward*forward)+f32(strafe*strafe))
    if squared<f32(0.0001) then return 0,0 end
    local length=f32(math.sqrt(squared))
    local scale=f32(f32(acceleration)/math.max(1,length))
    forward,strafe=f32(forward*scale),f32(strafe*scale)
    local sine,cosine=sincos(yaw,fast,false)
    return f32(f32(strafe*cosine)-f32(forward*sine)),
        f32(f32(forward*cosine)+f32(strafe*sine))
end
local function acceleration(c,ground,sprinting)
    if not ground then return sprinting and f32(f32(0.02)*(1+0.3)) or f32(0.02) end
    local under=block(c.x,c.y-1,c.z)
    local friction=f32(f32(under and finite(under.slipperiness) and under.slipperiness or 0.6)*f32(0.91))
    local speed=f32(0.1)*(sprinting and (1+f32(0.3)) or 1)
    for _,effect in ipairs({{1,0.2},{2,-0.15}}) do
        local potion=read(player.get_effect,nil,effect[1])
        if potion and finite(potion.amplifier) then
            speed=speed*math.max(0,1+f32(effect[2])*(potion.amplifier+1))
        end
    end
    local factor=f32(f32(0.16277136)/f32(f32(friction*friction)*friction))
    return f32(f32(speed)*factor)
end
local function smallMotion(v) return math.abs(v)<0.005 and 0 or v end
local function nativeJumpImpulse(p,yaw,fast)
    if p.autoJump and finite(p.autoImpulseX) and finite(p.autoImpulseZ) then
        return p.autoImpulseX,p.autoImpulseZ
    end
    local si,co=sincos(yaw,fast,true)
    return -f32(si*f32(0.2)),f32(co*f32(0.2))
end
local function correctedJumpImpulse(p,yaw,fast)
    local si,co=sincos(yaw,fast,true)

    return -f32(si*f32(0.2)),f32(co*f32(0.2))
end
local function rawInput()
    local input=read(player.get_input,nil)
    local f=input and finite(input.x) and input.x or 0
    local s=input and finite(input.y) and input.y or 0
    return f,s
end
local function inputDampingHint(forward,strafe)

    local magnitude=math.max(math.abs(forward),math.abs(strafe))
    if magnitude<0.0001 then return nil end
    for _,base in ipairs({1,0.3,0.2,0.06}) do
        if math.abs(magnitude-f32(f32(base)*f32(0.98)))<0.000001 then return 1 end
        if math.abs(magnitude-f32(base))<0.000001 then return 0.98 end
    end
    return nil
end
local function faceEquals(value,face)
    return value==enums.facing[face.name] or (type(value)=="number" and value==face.id)
end
local function safeFaceHit(hit,target)
    local margin=1
    for _,axis in ipairs({"x","y","z"}) do
        if not finite(hit[axis]) then return nil end
        local value=hit[axis]-target[axis]
        local normal=target.face[axis]
        if normal~=0 then
            if math.abs(value-(normal>0 and 1 or 0))>RAY_EPSILON then return nil end
        else
            local edge=math.min(value,1-value)
            if edge<FACE_MARGIN then return nil end
            margin=math.min(margin,edge)
        end
    end
    return margin
end
local function projectFace(origin,yaw,pitch,target,fast)
    local dx,dy,dz=lookVector(yaw,pitch,fast)
    local f=target.face
    local denominator=dx*f.x+dy*f.y+dz*f.z

    if denominator>=-0.000001 then return nil end
    local px,py,pz=target.x+(f.x>0 and 1 or 0),target.y+(f.y>0 and 1 or 0),target.z+(f.z>0 and 1 or 0)
    local time=((px-origin.x)*f.x+(py-origin.eye)*f.y+(pz-origin.z)*f.z)/denominator
    if not finite(time) or time<0 or time>REACH then return nil end
    local hit={x=origin.x+dx*time,y=origin.eye+dy*time,z=origin.z+dz*time}
    if not safeFaceHit(hit,target) then return nil end
    return hit
end
local function clearFullCubePath(origin,hit,target)
    local start={x=origin.x,y=origin.eye,z=origin.z}

    for x=math.floor(math.min(start.x,hit.x)),math.floor(math.max(start.x,hit.x)) do
        for y=math.floor(math.min(start.y,hit.y)),math.floor(math.max(start.y,hit.y)) do
            for z=math.floor(math.min(start.z,hit.z)),math.floor(math.max(start.z,hit.z)) do
                if x~=target.x or y~=target.y or z~=target.z then
                    local b=block(x,y,z)
                    if b==nil then return false end
                    if b and (b.full_block==true or b.name=="tile.glass" or b.name=="tile.stainedglass") then
                        local cell={x=x,y=y,z=z}
                        local entry,exit=0,1
                        for _,axis in ipairs({"x","y","z"}) do
                            local direction=hit[axis]-start[axis]
                            if math.abs(direction)<1e-12 then
                                if start[axis]<cell[axis] or start[axis]>cell[axis]+1 then exit=-1;break end
                            else
                                local a=(cell[axis]-start[axis])/direction
                                local c=(cell[axis]+1-start[axis])/direction
                                entry=math.max(entry,math.min(a,c));exit=math.min(exit,math.max(a,c))
                            end
                        end
                        if entry<=exit and entry<1-0.000001 and exit>0.000001 then return false end
                    end
                end
            end
        end
    end
    return true
end
local function wireCoordinate(value)
    return trunc(f32(f32(value)*16))/16
end
local function publishedSupportRay(origin,yaw,pitch,target,fast)

    local dx,dy,dz=lookVector(yaw,pitch,fast)
    local direction={x=dx,y=dy,z=dz}
    local start={x=origin.x,y=origin.eye,z=origin.z}
    local entry,exit=0,REACH
    for _,axis in ipairs({"x","y","z"}) do
        local d=direction[axis]
        if math.abs(d)<1e-12 then
            if start[axis]<target[axis] or start[axis]>target[axis]+1 then return false end
        else
            local a=(target[axis]-start[axis])/d
            local b=(target[axis]+1-start[axis])/d
            entry=math.max(entry,math.min(a,b));exit=math.min(exit,math.max(a,b))
        end
    end
    if entry>exit then return false end
    local hit={x=start.x+dx*entry,y=start.y+dy*entry,z=start.z+dz*entry}
    return clearFullCubePath(origin,hit,target)
end
local function exact(yaw,pitch,target,origin)
    if target==nil or state.rays>=RAY_LIMIT then state.rayReason="No target / ray budget reached";return nil end
    state.rays=state.rays+1
    local ray=read(player.ray_cast,nil,yaw,pitch,REACH)
    if ray==nil or ray.hit_type~=enums.hittype.BLOCK or ray.pos==nil then state.rayReason="Native ray did not hit a block";return nil end
    if ray.pos.x~=target.x or ray.pos.y~=target.y or ray.pos.z~=target.z
        or not faceEquals(ray.side_hit,target.face) then state.rayReason="Native support / face differs";return nil end
    local hit=ray.hit_vec
    if hit==nil or not finite(hit.x) or not finite(hit.y) or not finite(hit.z) then state.rayReason="Native hit coordinates unavailable";return nil end
    local margin=safeFaceHit(hit,target)
    if not margin then state.rayReason="Hit lies near a face edge / outside the face";return nil end
    local x=origin and origin.x or read(player.get_position_x,nil)
    local y=origin and origin.y or read(player.get_position_y,nil)
    local z=origin and origin.z or read(player.get_position_z,nil)
    if not finite(x) or not finite(y) or not finite(z) then state.rayReason="Current eye position unavailable";return nil end
    origin=origin or {x=x,z=z,eye=y+eyeHeight(read(player.is_sneaking,false))}
    local modes=state.mathKnown and {state.fastMath} or {false,true,"aligned"}
    for _,fast in ipairs(modes) do
        local projected=projectFace(origin,yaw,pitch,target,fast)
        if projected then
            local agrees=true
            for _,axis in ipairs({"x","y","z"}) do
                local error=math.min(math.abs(projected[axis]-hit[axis]),math.abs(f32(projected[axis])-hit[axis]))
                if error>RAY_EPSILON then agrees=false;break end
            end
            if agrees then
                state.rayReason=nil
                return {yaw=yaw,pitch=pitch,hit=hit,margin=margin,profile=fast}
            end
        end
    end
    state.rayReason="Native ray origin / MathHelper geometry mismatch"
    return nil
end
local function identifyMath(c,p)

    local probes={{c.yaw+13.713,51.171},{c.yaw-27.413,61.317},
        {c.yaw+13.713,89.171},{c.yaw-27.413,84.317}}
    if p.strict then
        probes[#probes+1]={p.strict.yaw+0.713,clamp(p.strict.pitch+0.317,-89.9,89.9)}
        probes[#probes+1]={p.strict.yaw-0.919,clamp(p.strict.pitch-0.413,-89.9,89.9)}
    end
    local scores={["vanilla"]=0,["half"]=0,["aligned"]=0}
    local observations=0
    local modes={{key="vanilla",value=false},{key="half",value=true},{key="aligned",value="aligned"}}
    for _,probe in ipairs(probes) do
        local yaw,pitch=f32(probe[1]),f32(probe[2])
        local ray=read(player.ray_cast,nil,yaw,pitch,REACH)
        if ray and ray.hit_type==enums.hittype.BLOCK and ray.pos and ray.hit_vec then
            local b=block(ray.pos.x,ray.pos.y,ray.pos.z)
            local face
            for _,candidate in ipairs(FACES) do if faceEquals(ray.side_hit,candidate) then face=candidate;break end end
            local hit=ray.hit_vec
            if face and support(b) and finite(hit.x) and finite(hit.y) and finite(hit.z) then
                observations=observations+1
                for _,mode in ipairs(modes) do
                    local dx,dy,dz=lookVector(yaw,pitch,mode.value)
                    local denominator=face.x~=0 and dx or (face.y~=0 and dy or dz)
                    local coordinate=face.x~=0 and c.x or (face.y~=0 and c.eye or c.z)
                    local cell=face.x~=0 and ray.pos.x or (face.y~=0 and ray.pos.y or ray.pos.z)
                    local normal=face.x~=0 and face.x or (face.y~=0 and face.y or face.z)
                    local plane=cell+(normal>0 and 1 or 0)
                    if math.abs(denominator)<1e-12 then scores[mode.key]=scores[mode.key]+1
                    else
                        local time=(plane-coordinate)/denominator
                        local hx,hy,hz=c.x+dx*time,c.eye+dy*time,c.z+dz*time
                        local doubleError=(hx-hit.x)^2+(hy-hit.y)^2+(hz-hit.z)^2
                        local floatError=(f32(hx)-hit.x)^2+(f32(hy)-hit.y)^2+(f32(hz)-hit.z)^2
                        scores[mode.key]=scores[mode.key]+math.min(doubleError,floatError)
                    end
                end
            end
        end
    end
    if observations==0 then return false end
    table.sort(modes,function(a,b) return scores[a.key]<scores[b.key] end)
    local first,second=scores[modes[1].key],scores[modes[2].key]
    if first<=observations*1e-12 and second>math.max(1e-13,first*16) then
        state.fastMath,state.mathKnown=modes[1].value,true
        return true
    end
    return false
end
local function ready()
    local acquired,reason=acquireNativeState()
    if not acquired then return false,reason end
    if nativeEnabled() then return false,"Native Scaffold was enabled again" end
    if nativeLease and read(modules.is_enabled,false,"Sprint") then
        return false,"Native Sprint was enabled again; conflicting sprint controller"
    end
    for _,name in ipairs({"scaffoldsnap","Moduelhelper","sca","scaffold2","betterscaffold"}) do
        if read(modules.is_enabled,false,name) then return false,"Other scaffold enabled: "..name end
    end
    if (read(modules.is_enabled,false,"KillAura") or read(modules.is_enabled,false,"TargetStrafe"))
        and read(client.current_kill_aura_target,-1)>=0 then
        return false,"Combat movement / rotation controller has an active target"
    end
    if read(inventory.current_inventory,nil)~=nil then return false,"Inventory open" end
    if not hasBlocks() then return false,state.inventoryReason end
    if read(player.is_in_water,false) or read(player.is_in_lava,false) then return false,"In liquid" end
    if read(player.is_on_ladder,false) or read(player.is_riding,false)
        or read(player.is_in_cobweb,false) then return false,"Ladder / riding / cobweb" end
    if state.tick<state.cooldownUntil then return false,"Resynchronising after server correction" end
    return true
end
local function closePosition(a,b)
    if not a or not b then return false end
    for _,axis in ipairs({"x","y","z"}) do
        if not finite(a[axis]) or not finite(b[axis]) or math.abs(a[axis]-b[axis])>0.001 then return false end
    end
    return true
end
local function positionInOrdinaryFrame(a,b)
    if not a or not b then return false end
    for _,axis in ipairs({"x","y","z"}) do
        if not finite(a[axis]) or not finite(b[axis]) then return false end
    end

    local dx,dy,dz=a.x-b.x,a.y-b.y,a.z-b.z
    return dx*dx+dy*dy+dz*dz<=0.0009+1e-12
end
local function selectSlot(c)
    local current=read(player.get_held_item_slot,nil)
    if not finite(current) or current<0 or current>8 then return nil end
    local chosen,count=nil,-1
    local hotbar=utils.inventory.hotbar()
    local held=hotbar.slots[current]
    local earlySwitch=setting("hypixel-block-switch",false)
    if not earlySwitch then state.lowStackSwitched={} end
    for slot=0,8 do
        local stack=hotbar.slots[slot]
        if not stack or stack.count>4 then state.lowStackSwitched[slot]=nil end
    end
    local switchingLow=earlySwitch and held and held.count<=4 and not state.lowStackSwitched[current]
    if switchingLow then

        for offset=1,8 do
            local slot=(current+offset)%9
            local stack=hotbar.slots[slot]
            if stack and not state.lowStackSwitched[slot] and (chosen==nil
                or (setting("most-blocks",false) and stack.count>count)) then
                chosen,count=slot,stack.count
            end
        end
        if chosen==nil then return current end
    end

    if chosen==nil then
        if held then return current end
        for slot=0,8 do
            local stack=hotbar.slots[slot]
            if stack then
                if chosen==nil or (setting("most-blocks",false) and stack.count>count) then
                    chosen,count=slot,stack.count
                end
            end
        end
    end
    if chosen==nil then return nil end
    if chosen==current then return current end
    if slotLease==nil then slotLease={owner=state.owner,original=current,applied=chosen}
    elseif current~=slotLease.applied then slotLease.original=current end
    local ok=pcall(player.set_held_item,chosen)
    if not ok or read(player.get_held_item_slot,nil)~=chosen then return nil end
    if switchingLow then state.lowStackSwitched[current]=true end
    slotLease.applied=chosen
    return chosen
end
local function validatePlacementPacket(a,event)
    local t,complete=a.target,true
    if event.cancel==true then return "Own C08 cancelled by another hook",false end
    for _,axis in ipairs({"x","y","z"}) do
        if not finite(event[axis]) then complete=false
        elseif event[axis]~=t[axis] then return "C08 support differs from the validated ray",true end
    end
    if event.direction==nil then complete=false
    elseif not faceEquals(event.direction,t.face) then return "C08 face differs from the validated ray",true end

    for _,axis in ipairs({"x","y","z"}) do
        local value=event["facing_"..axis]
        local wanted=wireCoordinate(a.hit[axis]-t[axis])
        if not finite(value) then complete=false
        elseif value<0 or value>1 or wireCoordinate(value)~=wanted then
            return "C08 relative hit coordinate differs from the validated ray",true
        end
    end
    return nil,complete
end
local function alignPlacementPacket(a,event)
    if event.cancel==true then return end

    for _,axis in ipairs({"x","y","z"}) do
        if finite(event[axis]) and event[axis]~=a.target[axis] then
            event[axis]=a.target[axis]
        end
    end
    if finite(event.direction) and event.direction~=a.target.face.id then
        event.direction=a.target.face.id
    end
    for _,axis in ipairs({"x","y","z"}) do
        local value=event["facing_"..axis]
        local relative=f32(a.hit[axis]-a.target[axis])
        if finite(value) and (value<0 or value>1 or wireCoordinate(value)~=wireCoordinate(relative)) then

            event["facing_"..axis]=relative
        end
    end

end

local function direction(yaw,forward,strafe)
    if forward<0 then yaw=yaw+180 end
    if strafe~=0 then yaw=yaw-90*(forward==0 and 1 or 0.5*sign(forward))*sign(strafe) end
    return wrap(yaw)
end
local function movement(c)

    local f,s=rawInput()
    local down=client.is_key_down
    local kf=(read(down,false,17) and 1 or 0)-(read(down,false,31) and 1 or 0)
    local ks=(read(down,false,30) and 1 or 0)-(read(down,false,32) and 1 or 0)
    if kf~=0 or ks~=0 then f,s=kf,ks end
    return direction(c.yaw,f,s),f,s
end

local function fixedInput(cameraYaw,yaw,forward,strafe)
    if math.abs(forward)+math.abs(strafe)<0.0001 then return 0,0 end
    if not setting("move-fix",true) then return forward,strafe end
    local angle=wrap(direction(cameraYaw,forward,strafe)-yaw)
    local magnitude=math.max(math.abs(forward),math.abs(strafe))
    local f,s=0,0

    if angle>=-67.5 and angle<67.5 then f=1 elseif angle>=112.5 or angle< -112.5 then f=-1 end
    if angle>=22.5 and angle<157.5 then s=-1
    elseif angle>=-157.5 and angle< -22.5 then s=1 end
    return f*magnitude,s*magnitude
end
local function sprintDecision(forward)
    if not setting("sprint",true) then return false,"Allow Sprint off" end
    if read(player.is_sneaking,false) then return false,"sneaking" end
    if read(player.is_using_item,false) then return false,"using item" end
    if read(player.collided_horizontally,false) then return false,"horizontal collision" end
    if read(player.get_food_amount,20)<=6 then return false,"low food" end
    if forward<0.8 then return false,"corrected forward below 0.8" end
    return true,"forward input"
end
local function atan2(y,x)
    if x>0 then return math.atan(y/x) end
    if x<0 then return math.atan(y/x)+(y>=0 and math.pi or -math.pi) end
    return y>0 and math.pi/2 or (y<0 and -math.pi/2 or 0)
end
local function shiftDown() return read(client.is_key_down,false,42) or read(client.is_key_down,false,54) end
local function tellyEnabled() return setting("telly",true) and (not setting("telly-shift",false) or shiftDown()) end
local function jumpDown() return read(client.is_key_down,false,57) end
local function validTarget(c,t)
    if not t or rejected(t) or not supportAt(t.x,t.y,t.z) then return false end
    local x,y,z=t.x+t.face.x,t.y+t.face.y,t.z+t.face.z
    if not replaceable(block(x,y,z)) then return false end

    if x<c.x+0.3 and x+1>c.x-0.3 and z<c.z+0.3 and z+1>c.z-0.3
        and y<c.y+1.8 and y+1>c.y+0.00001 then return false end
    local hx,hy,hz=t.x+0.5+t.face.x*0.5,t.y+0.5+t.face.y*0.5,t.z+0.5+t.face.z*0.5
    return (hx-c.x)^2+(hy-c.eye)^2+(hz-c.z)^2<=REACH^2+1
end
local function closestFace(c,t,lastYaw,lastPitch)
    if not validTarget(c,t) then return nil end
    local _,rawForward,rawStrafe=movement(c)

    local ascendingTelly=tellyEnabled() and jumpDown()
    local preferSprint=not ascendingTelly and setting("prefer-sprint-ray",true) and setting("sprint",true)
        and rawForward>0.1
    local key=cellKey(t.x,t.y,t.z)..":"..t.face.id
    local cached=state.faceCache[key]
    if cached and cached.origin==c and cached.yaw==lastYaw and cached.pitch==lastPitch
        and cached.forward==rawForward and cached.strafe==rawStrafe and cached.preferSprint==preferSprint then
        return cached.hit or nil
    end
    local function remember(hit)
        state.faceCache[key]={origin=c,yaw=lastYaw,pitch=lastPitch,forward=rawForward,
            strafe=rawStrafe,preferSprint=preferSprint,hit=hit or false}
        return hit
    end
    local function sprintRay(hit)
        local forward=fixedInput(c.yaw,hit.yaw,rawForward,rawStrafe)
        return sprintDecision(forward)
    end
    local previous=exact(lastYaw,lastPitch,t,c)
    if previous and (not preferSprint or sprintRay(previous)) then return remember(previous) end
    local points={}
    local face=t.face
    local px,py,pz=t.x+0.5+face.x*0.5,t.y+0.5+face.y*0.5,t.z+0.5+face.z*0.5
    local inset=0.03
    local function add(x,y,z) points[#points+1]={x=x,y=y,z=z} end
    add(px,py,pz)

    for _,a in ipairs({inset,0.5,1-inset}) do
        for _,b in ipairs({inset,0.5,1-inset}) do
            if face.x~=0 then add(px,t.y+a,t.z+b)
            elseif face.y~=0 then add(t.x+a,py,t.z+b)
            else add(t.x+a,t.y+b,pz) end
        end
    end
    if face.y~=0 then
        add(clamp(c.x,t.x+inset,t.x+1-inset),py,clamp(c.z,t.z+inset,t.z+1-inset))
    else
        local dx,dz=-math.sin(lastYaw*math.pi/180),math.cos(lastYaw*math.pi/180)
        if face.x~=0 and math.abs(dx)>0.0001 then
            local time=(px-c.x)/dx
            if time>0 then pz=clamp(c.z+time*dz,t.z+inset,t.z+1-inset) end
        elseif face.z~=0 and math.abs(dz)>0.0001 then
            local time=(pz-c.z)/dz
            if time>0 then px=clamp(c.x+time*dx,t.x+inset,t.x+1-inset) end
        end
        for _,h in ipairs({0.1,0.5,0.9}) do add(px,t.y+h,pz) end
        add(px,clamp(c.eye,t.y+inset,t.y+1-inset),pz)
    end

    local cx,cy,cz=t.x+0.5+face.x*0.5,t.y+0.5+face.y*0.5,t.z+0.5+face.z*0.5
    local dx,dz=-math.sin(lastYaw*math.pi/180),math.cos(lastYaw*math.pi/180)
    local denominator=face.x*dx+face.z*dz
    local rx,ry,rz=c.x+100*dx,c.eye,c.z+100*dz
    if math.abs(denominator)>0.000001 then
        local time=(face.x*(cx-c.x)+face.y*(cy-c.eye)+face.z*(cz-c.z))/denominator
        if time>0 then
            local hx,hz=c.x+time*dx,c.z+time*dz
            local inFace
            if face.x~=0 then inFace=c.eye>=t.y and c.eye<=t.y+1 and hz>=t.z and hz<=t.z+1
            else inFace=hx>=t.x and hx<=t.x+1 and c.eye>=t.y and c.eye<=t.y+1 end
            if inFace then rx,rz=hx,hz end
        end
    end
    if face.x~=0 then add(cx,clamp(ry,t.y+inset,t.y+1-inset),clamp(rz,t.z+inset,t.z+1-inset))
    elseif face.y~=0 then add(clamp(rx,t.x+inset,t.x+1-inset),cy,clamp(rz,t.z+inset,t.z+1-inset))
    else add(clamp(rx,t.x+inset,t.x+1-inset),clamp(ry,t.y+inset,t.y+1-inset),cz) end
    local best,bestDiff,bestSprint=previous,previous and 0 or nil,previous and preferSprint and sprintRay(previous) or false
    for _,point in ipairs(points) do
        local dx,dy,dz=point.x-c.x,point.y-c.eye,point.z-c.z
        local yaw=near(atan2(dz,dx)*180/math.pi-90,lastYaw)
        local pitch=-atan2(dy,math.sqrt(dx*dx+dz*dz))*180/math.pi
        if math.abs(pitch)>89.9 then yaw=lastYaw end
        yaw,pitch=f32(yaw),f32(clamp(pitch,-90,90))
        local hit=exact(yaw,pitch,t,c)
        local diff=math.abs(wrap(yaw-lastYaw))
        if hit then
            local candidateSprint=preferSprint and sprintRay(hit) or false
            if best==nil or (candidateSprint and not bestSprint)
                or (candidateSprint==bestSprint and (diff<bestDiff-0.05
                or (math.abs(diff-bestDiff)<=0.05 and hit.margin>best.margin))) then
                best,bestDiff,bestSprint=hit,diff,candidateSprint
            end
        end
    end

    return remember(best)
end
local function searchedPlace(c,tx,ty,tz,lastYaw,lastPitch,directOnly)
    if not replaceable(block(tx,ty,tz)) then return nil end

    local neighbours={}
    for order,id in ipairs({5,4,2,3,1}) do
        local face=FACES[id]
        local t={x=tx-face.x,y=ty-face.y,z=tz-face.z,face=face}
        if validTarget(c,t) then
            local viewed=state.searchSupport
            local priority=viewed and viewed.x==t.x and viewed.y==t.y and viewed.z==t.z and 0 or 1
            neighbours[#neighbours+1]={target=t,priority=priority,order=order}
        end
    end
    table.sort(neighbours,function(a,b)
        return a.priority<b.priority or (a.priority==b.priority and a.order<b.order)
    end)
    for _,candidate in ipairs(neighbours) do
        if closestFace(c,candidate.target,lastYaw,lastPitch) then return candidate.target end
    end
    if directOnly then return nil end

    local bx,by,bz=math.floor(c.x),math.floor(c.y),math.floor(c.z)
    for _,off in ipairs(SEARCH_OFFSETS) do

        if state.rays>=RAY_LIMIT-128 then break end
        local x,y,z=bx+off.x,by+off.y,bz+off.z
        if supportAt(x,y,z) and (x+0.5-c.x)^2+(y+0.5-c.eye)^2+(z+0.5-c.z)^2<=(REACH+1)^2 then
            local best,bestDiff,bestDistance
            for _,face in ipairs(FACES) do
                if face.y==0 or (face.y==1 and y+1==ty) then
                    local t={x=x,y=y,z=z,face=face}
                    if validTarget(c,t) then
                        local hit=closestFace(c,t,lastYaw,lastPitch)
                        if hit then
                            local diff=math.abs(wrap(hit.yaw-lastYaw))
                            local distance=(x+face.x-tx)^2+(y+face.y-ty)^2+(z+face.z-tz)^2
                            if best==nil or distance<bestDistance or (distance==bestDistance and diff<bestDiff) then
                                best,bestDiff,bestDistance=t,diff,distance
                            end
                        end
                    end
                end
            end
            if best then return best end
        end
    end
    return nil
end
local function targetOnRoute(c,t,ty,mx,mz)
    if not validTarget(c,t) or t.y+t.face.y~=ty then return false end
    local destination={x=t.x+t.face.x,z=t.z+t.face.z}
    local start={x=c.x,z=c.z}
    local delta={x=mx*3,z=mz*3}
    local entry,exit=0,1
    for _,axis in ipairs({"x","z"}) do
        local low,high=destination[axis]-0.3,destination[axis]+1.3
        if math.abs(delta[axis])<1e-12 then
            if start[axis]<low or start[axis]>high then return false end
        else
            local a=(low-start[axis])/delta[axis]
            local b=(high-start[axis])/delta[axis]
            entry=math.max(entry,math.min(a,b));exit=math.min(exit,math.max(a,b))
        end
    end
    return entry<=exit
end
local function footprintSupported(c)
    local y=math.floor(c.y-0.0001)
    for x=math.floor(c.x-0.29999),math.floor(c.x+0.29999) do
        for z=math.floor(c.z-0.29999),math.floor(c.z+0.29999) do
            if supportAt(x,y,z) then return true end
        end
    end
    return false
end

restoreFreeze=function()
    local held=state.freeze
    state.freeze=nil
    if held and held.owner==read(player.get_id,nil) then
        local p=state.plan
        if p and p.frozen and not p.impulse then
            local moved=held.event and (held.event.x~=0 or held.event.y~=0 or held.event.z~=0)
            local fall=read(player.get_fall_distance,nil)
            local changedFall=finite(held.fall) and finite(fall) and math.abs(held.fall-fall)>0.000001
            if moved or changedFall then

                state.cooldownUntil=math.max(state.cooldownUntil,state.tick+5)
                return
            end
            pcall(player.set_motion_x,held.x)
            pcall(player.set_motion_y,held.y)
            pcall(player.set_motion_z,held.z)
        end
    end
end
restoreSneak=function()
    if state.sneakLease and state.sneakLease.owner==read(player.get_id,nil) then
        pcall(player.set_sneaking,shiftDown())
    end
    state.sneakLease=nil
end
local function setSneak(value)
    if not state.sneakLease then state.sneakLease={owner=state.owner} end
    if not pcall(player.set_sneaking,value) then return false end
    return true
end
local function freezeMove(p,event)
    local previous={x=read(player.get_motion_x,nil),y=read(player.get_motion_y,nil),z=read(player.get_motion_z,nil)}
    if not finite(previous.x) or not finite(previous.y) or not finite(previous.z) then
        p.clutch=false;state.clutchTick=0;return false
    end
    local okX=pcall(player.set_motion_x,0)
    local okY=pcall(player.set_motion_y,0)
    local okZ=pcall(player.set_motion_z,0)
    if not okX or not okY or not okZ or read(player.get_motion_x,nil)~=0
        or read(player.get_motion_y,nil)~=0 or read(player.get_motion_z,nil)~=0 then
        pcall(player.set_motion_x,previous.x);pcall(player.set_motion_y,previous.y);pcall(player.set_motion_z,previous.z)
        p.clutch=false;state.clutchTick=0;return false
    end

    state.freeze={owner=state.owner,x=p.beforeX,y=p.beforeY,z=p.beforeZ,event=event,
        fall=read(player.get_fall_distance,nil)}
    p.frozen=true
    event.x,event.y,event.z=0,0,0
    return true
end
local function planRotation(c,p)
    local lastYaw,lastPitch=state.serverYaw or c.yaw,state.serverPitch or read(player.get_pitch,0)

    local manualKey=jumpDown()
    local requestedTelly=tellyEnabled()
    local _,forward,strafe=movement(c)
    local moving=math.abs(forward)+math.abs(strafe)>0.1
    local previousTarget=state.aimTarget
    local carryManual=state.manualJumpActive and not (p.wasGround and requestedTelly and moving)
    local manualJump=carryManual or (manualKey and (not requestedTelly or not moving))

    state.manualJumpActive=manualJump and (manualKey or not p.wasGround)
    local telly=requestedTelly and not manualJump
    local placeTick=clamp(setting("place-tick",4),1,6)
    if state.baseY==nil or p.wasGround or not telly then state.baseY=math.floor(c.y-1) end
    if manualJump or (telly and manualKey) then state.baseY=math.floor(c.y)-1 end
    local viewed=read(player.ray_cast,nil,lastYaw,lastPitch,REACH)
    state.rays=state.rays+1
    state.searchSupport=viewed and viewed.hit_type==enums.hittype.BLOCK and viewed.pos or nil
    local ty=math.floor(state.baseY)
    local mx,mz=read(player.get_motion_x,0),read(player.get_motion_z,0)
    if moving then

        local ax,az=inputVector(c.yaw,forward,strafe,0.98,
            acceleration(c,p.wasGround,read(player.is_sprinting,false)),state.fastMath)
        mx,mz=smallMotion(mx)+ax,smallMotion(mz)+az
    end
    local length=math.sqrt(mx*mx+mz*mz)
    if length>1 then mx,mz=mx/length,mz/length end
    local target
    if replaceable(block(c.x,c.y,c.z)) then

        if targetOnRoute(c,state.aimTarget,ty,mx,mz)
            and closestFace(c,state.aimTarget,lastYaw,lastPitch) then target=state.aimTarget end
        local tx,tz=math.floor(c.x),math.floor(c.z)
        if not target then target=searchedPlace(c,tx,ty,tz,lastYaw,lastPitch,true) end

        if not target and (moving or length>0.01) then
            local visited={[cellKey(tx,ty,tz)]=true}
            for step=1,3 do
                local aheadX,aheadZ=math.floor(c.x+mx*step),math.floor(c.z+mz*step)
                local key=cellKey(aheadX,ty,aheadZ)
                if not visited[key] then
                    visited[key]=true
                    target=searchedPlace(c,aheadX,ty,aheadZ,lastYaw,lastPitch,true)
                    if target then break end
                end
            end
        end
        if not target then target=searchedPlace(c,tx,ty,tz,lastYaw,lastPitch) end
    end

    if (not target or not targetOnRoute(c,target,ty,mx,mz))
        and validTarget(c,previousTarget) and closestFace(c,previousTarget,lastYaw,lastPitch) then
        target=previousTarget
    end
    state.aimTarget=target

    local predictedY,my=c.y,read(player.get_motion_y,0)
    for i=1,2 do predictedY=predictedY+my; my=(my-0.08)*0.98 end
    if setting("clutch",false) and target and not p.wasGround
        and target.y>predictedY and state.clutchTick<=8 then
        state.baseY=c.y-1
        target=searchedPlace(c,math.floor(c.x),math.floor(state.baseY),math.floor(c.z),lastYaw,lastPitch)
        state.aimTarget=target
        p.clutch=target~=nil
        if p.clutch then state.clutchTick=state.clutchTick+1 else state.clutchTick=0 end
    else state.clutchTick=0 end
    local strict=target and closestFace(c,target,lastYaw,lastPitch)
    local jitter=setting("source-jitter",false)
    p.groundSupported=p.wasGround and footprintSupported(c)
    local wantsTellyJump=telly and moving and (manualKey or setting("auto-jump",true))
    local idleHeading=p.wasGround and wantsTellyJump and direction(c.yaw,forward,strafe) or c.yaw
    local idleYaw=idleHeading-(jitter and math.random() or 0.5)
    local idlePitch=85.5-(jitter and math.random() or 0.5)
    local yaw,pitch=state.rotYaw or lastYaw,state.rotPitch or lastPitch
    local watchdog,grim=setting("watchdog",true),setting("grim",false)
    if manualJump then

        if strict then yaw,pitch=strict.yaw,strict.pitch
        else yaw,pitch=lastYaw,lastPitch end
        if p.wasGround then state.lock=false end
    elseif not watchdog and not grim and target then

        yaw,pitch=strict and strict.yaw or lastYaw,strict and strict.pitch or lastPitch
    end
    if not manualJump and (watchdog or grim) then
        local bestYaw=strict and strict.yaw or lastYaw
        local bestPitch=strict and strict.pitch or lastPitch
        if state.airTicks>0 or (setting("telly-shift",false) and not shiftDown()) or not setting("telly",true) then

            yaw,pitch=bestYaw,bestPitch
            state.lock=strict~=nil
        elseif state.groundTicks>=1 then
            state.lock=false

            if strict or not p.groundSupported or not wantsTellyJump then
                yaw,pitch=strict and strict.yaw or lastYaw,strict and strict.pitch or lastPitch
            else yaw,pitch=idleYaw,idlePitch end
        end
    end

    local jumpReady=manualKey or ((state.groundTicks>=2 or p.landed) and not state.jumpedGround)
    p.recoveringSprint=wantsTellyJump and p.groundSupported and jumpReady and not p.clutch
    if p.recoveringSprint then
        yaw,pitch=idleYaw,idlePitch
        state.lock=false
    end
    yaw,pitch=f32(near(yaw,lastYaw)),f32(clamp(pitch,-90,90))
    if jitter then
        local nudged=f32(clamp(pitch-(0.002+math.random()*0.004),-90,90))
        if not strict or exact(yaw,nudged,target,c) then pitch=nudged end
    end

    local dx,dz=target and target.x+target.face.x,target and target.z+target.face.z
    local approachingGap=target and target.y+target.face.y==ty
        and ((dx==math.floor(c.x) and dz==math.floor(c.z))
        or (dx==math.floor(c.x+mx) and dz==math.floor(c.z+mz)))
    p.earlyEdge=setting("early-edge-place",true) and telly and not p.wasGround and moving
        and strict~=nil and approachingGap==true and state.airTicks>0
        and state.airTicks<placeTick and targetOnRoute(c,target,ty,mx,mz)

    p.skipPlacement=p.recoveringSprint or (telly and state.airTicks<placeTick and not p.earlyEdge)
    p.holdReason=p.recoveringSprint and "Telly ground recovery"
        or ((telly and state.airTicks<placeTick and not p.earlyEdge) and "Telly Place Tick") or nil

    local seekSprint=false
    if strict and setting("prefer-sprint-ray",true) then
        local oldForward=fixedInput(c.yaw,lastYaw,forward,strafe)
        local aimForward=fixedInput(c.yaw,strict.yaw,forward,strafe)
        seekSprint=not sprintDecision(oldForward) and sprintDecision(aimForward)
    end

    local published=not telly and (not state.placementPrimed or not seekSprint)
        and not p.skipPlacement and target and exact(lastYaw,lastPitch,target,c)
    if published then yaw,pitch=published.yaw,published.pitch end
    state.rotYaw,state.rotPitch=yaw,pitch
    p.yaw,p.pitch,p.target,p.telly,p.strict=yaw,pitch,target,telly,strict
    p.manualJump,p.publishedRay=manualJump,published~=nil and published~=false
    p.reason=(grim and "Grim" or (watchdog and "Watchdog" or "Strict"))..
        (manualJump and " manual jump" or "")..
        " telly="..tostring(telly).." landed="..tostring(p.landed).." placeTick="..placeTick..
        " air="..state.airTicks.." ground="..state.groundTicks.." clutch="..state.clutchTick..
        " turn=direct earlyEdge="..tostring(p.earlyEdge==true)
end
local function prepare(newFrame)
    if newFrame then utils.frame.begin() end
    local c=context()
    if not c or state.preTick==c.tick then return end
    if not newFrame and state.plan and state.plan.tick==c.tick and state.plan.moved and not state.plan.sent then return end
    restoreFreeze();restoreSneak()
    state.preTick=c.tick
    state.blocks,state.faceCache,state.rays={},{},0

    local look,position=state.lastLookRef,state.lastPositionRef
    if look then
        if look.cancel==true then state.poseValid=false
        elseif finite(look.yaw) and finite(look.pitch) then
            state.serverYaw,state.serverPitch=look.yaw,look.pitch
        end
    end
    if position then
        if position.cancel==true then state.poseValid=false
        elseif finite(position.x) and finite(position.y) and finite(position.z) then
            state.serverPos={x=position.x,y=position.y,z=position.z}
        end
    end
    if state.lastSlotRef and state.lastSlotRef.cancel==true then
        state.syncedSlot,state.publishedSlot=nil,nil
        state.placementPrimed=false
    end
    if state.lastMovementRef and state.lastMovementRef.cancel==true then
        state.poseValid=false;state.publishedSlot=nil;state.placementPrimed=false
    end
    local previous=state.plan
    if previous and previous.moved and not previous.failed and previous.sent and look
        and (not finite(look.yaw) or not finite(look.pitch)
        or math.abs(wrap(look.yaw-previous.yaw))>0.001 or math.abs(look.pitch-previous.pitch)>0.001) then
        state.poseValid=false
        state.cooldownUntil=math.max(state.cooldownUntil,c.tick+2)
    end
    if not state.poseValid or (state.plan and state.plan.failed) then
        state.rotYaw=state.serverYaw or c.yaw
        state.rotPitch=state.serverPitch or read(player.get_pitch,0)
    end
    watchPlacements(c)
    local available,why=ready()
    if not available then suspend();state.prepareBlocked=why;return end
    local landed=false
    local grounded=read(player.is_on_ground,false)
    if state.counterTick~=c.tick then
        landed=grounded and state.previousGround==false
        state.previousGround=grounded
        if grounded then
            state.groundTicks,state.airTicks=state.groundTicks+1,0
            if landed and tellyEnabled() then state.placementPrimed=false end
        else state.groundTicks,state.airTicks=0,state.airTicks+1;state.jumpedGround=false end
        state.counterTick=c.tick
    end
    local selected=selectSlot(c)
    if selected==nil then
        suspend();state.prepareBlocked="Cannot select blocks";return
    end
    if state.preparedSlot~=selected then
        state.preparedSlot=selected
        state.publishedSlot=nil;state.placementPrimed=false
    end
    state.prepareBlocked=nil
    local rawX,rawZ=read(player.get_motion_x,0),read(player.get_motion_z,0)
    local mx,mz=smallMotion(rawX),smallMotion(rawZ)
    local p={tick=c.tick,frame=state.frame,slot=selected,origin=c,beforeX=mx,beforeY=read(player.get_motion_y,0),beforeZ=mz,
        rawBeforeX=rawX,rawBeforeZ=rawZ,wasGround=grounded,landed=landed,
        allow=setting("placement",true),moved=false,updated=false,sent=false,
        placementChecked=false,placementStage="independent pre-move placement",sprint=false}
    state.plan=p
    planRotation(c,p)
    identifyMath(c,p)
    if setting("telly-shift",false) then setSneak(false) end
    if not p.allow then p.yaw,p.pitch=c.yaw,read(player.get_pitch,0);p.skipPlacement=true end
    local _,f,s=movement(c)
    local vf=fixedInput(c.yaw,p.yaw,f,s)
    p.sprint,p.sprintReason=sprintDecision(vf)
    if not pcall(player.set_sprinting,p.sprint) then p.failed=true;return end

    if p.allow and p.telly and setting("auto-jump",true) and p.groundSupported and (state.groundTicks>=2 or p.landed)
        and not state.jumpedGround and math.abs(f)+math.abs(s)>0.1 and not jumpDown() and not p.clutch then
        if pcall(player.jump) then
            state.jumpedGround=true;p.autoJump=true
            local jx,jz=read(player.get_motion_x,nil),read(player.get_motion_z,nil)
            if finite(jx) and finite(jz) then
                p.autoImpulseX,p.autoImpulseZ=smallMotion(jx)-p.beforeX,smallMotion(jz)-p.beforeZ
            else p.failed=true end
        end
    end
end
local function move(event)
    state.stage="move"
    if event==nil or type(event)=="boolean" then return event end
    local c=context()
    local p=state.plan
    if c==nil then return event end
    if p==nil or p.sent or c.tick>p.tick+1 then

        if state.prepareBlocked then return event end

        return event
    end

    if not p.moved and not p.failed and c.tick==p.tick+1 then
        p.tick=c.tick

        state.preTick,state.counterTick=c.tick,c.tick
    end
    if p.tick~=c.tick then return event end
    if p.moved or p.failed then return event end
    local available,why=ready()
    if not available then suspend();state.prepareBlocked=why;return event end
    if not finite(event.x) or not finite(event.y) or not finite(event.z) then
        p.failed=true; return event
    end
    if not setting("move-fix",true) then
        p.moved=true;p.sprint=read(player.is_sprinting,false)
        if p.clutch then freezeMove(p,event) end
        p.placementChecked=true;placeBeforeMove(c,p)
        return event
    end
    local forward,strafe=rawInput()
    local using=read(player.is_using_item,false)
    local slow=using and 0.2 or 1
    local cameraYaw=read(player.get_yaw,p.origin.yaw)
    local inputYaws={cameraYaw}
    if math.abs(wrap(p.origin.yaw-cameraYaw))>0.000001 then inputYaws[#inputYaws+1]=p.origin.yaw end
    local matched
    local fittingModes,fittingDamping={},{}
    local expectedDamping=state.inputDamping or inputDampingHint(forward,strafe)

    local groundNow=read(player.is_on_ground,p.wasGround)
    local grounds={p.wasGround}
    if groundNow~=p.wasGround then grounds[#grounds+1]=groundNow end

    if not state.mathKnown then identifyMath(c,p) end
    local preferredMath=state.fastMath
    if preferredMath==nil then preferredMath=false end
    local mathModes={preferredMath}
    if not state.mathKnown then
        for _,mode in ipairs({false,true,"aligned"}) do
            if mode~=preferredMath then mathModes[#mathModes+1]=mode end
        end
    end
    for _,ground in ipairs(grounds) do
        for _,sprinting in ipairs({false,true}) do
            for _,damping in ipairs({0.98,1}) do
                for _,itemSlow in ipairs(using and {slow,1} or {1}) do
                  for _,fast in ipairs(mathModes) do
                   for _,inputYaw in ipairs(inputYaws) do
                    local ax,az=inputVector(inputYaw,f32(forward*f32(itemSlow)),f32(strafe*f32(itemSlow)),
                        damping,acceleration(p.origin,ground,sprinting),fast)
                    local jumps={{jump=false,x=0,z=0}}
                    if p.wasGround and event.y>=0.419 then
                        if p.autoJump then
                            local jx,jz=nativeJumpImpulse(p,cameraYaw,fast)
                            jumps[#jumps+1]={jump=true,x=jx,z=jz,yaw=cameraYaw}
                        elseif sprinting then

                            local yaws={cameraYaw,p.origin.yaw}
                            if finite(state.serverYaw) then yaws[#yaws+1]=state.serverYaw end
                            for _,jyaw in ipairs(yaws) do
                                local jx,jz=nativeJumpImpulse(p,jyaw,fast)
                                jumps[#jumps+1]={jump=true,x=jx,z=jz,yaw=jyaw}
                            end
                        end
                    end
                    for _,jump in ipairs(jumps) do
                            local jx,jz=jump.x,jump.z
                            local errorX=event.x-p.beforeX-ax-jx
                            local errorZ=event.z-p.beforeZ-az-jz
                            local score=errorX*errorX+errorZ*errorZ
                            local preferred=expectedDamping~=nil and damping==expectedDamping
                            if math.abs(errorX)<=0.000002 and math.abs(errorZ)<=0.000002
                                and (matched==nil or (preferred and not matched.preferredDamping)
                                or (preferred==matched.preferredDamping and score<matched.score)) then
                                matched={ground=ground,sprinting=sprinting,damping=damping,slow=itemSlow,fast=fast,jump=jump.jump,jumpYaw=jump.yaw,inputYaw=inputYaw,
                                    oldX=ax+jx,oldZ=az+jz,score=score,preferredDamping=preferred}
                            end
                            if math.abs(errorX)<=0.000002 and math.abs(errorZ)<=0.000002 then
                                fittingModes[fast==false and "vanilla" or (fast==true and "half" or "aligned")]=true
                                fittingDamping[damping]=true
                            end
                    end
                   end
                  end
                end
            end
        end
    end
    local dampingCount,uniqueDamping=0,nil
    for value in pairs(fittingDamping) do dampingCount=dampingCount+1;uniqueDamping=value end
    if dampingCount==1 and math.abs(forward)+math.abs(strafe)>0.0001 then

        state.inputDamping=uniqueDamping
    end
    if not matched then
        p.failed=true
        state.mathKnown=false
        return event
    end
    state.fastMath=matched.fast
    if not state.mathKnown then
        local count=0
        for _ in pairs(fittingModes) do count=count+1 end
        if count==1 then
            state.mathKnown=true
        end
    end
    local correctedSlow=matched.slow
    if using and math.max(math.abs(forward),math.abs(strafe))>0.21 then correctedSlow=0.2 end
    local movementYaw=matched.inputYaw
    local f,s=fixedInput(movementYaw,p.yaw,f32(forward*f32(correctedSlow)),f32(strafe*f32(correctedSlow)),matched.damping,matched.fast)
    local sprint,sprintReason=sprintDecision(f)

    local speedSprint=matched.ground and sprint or (not matched.ground and matched.sprinting)
    local physicalYaw=setting("move-fix",true) and p.yaw or movementYaw
    local ax,az=inputVector(physicalYaw,f,s,matched.damping,acceleration(p.origin,matched.ground,speedSprint),matched.fast)
    local jumpBoost=read(player.get_effect,nil,8)
    local jumpY=f32(0.42)+(jumpBoost and finite(jumpBoost.amplifier) and f32((jumpBoost.amplifier+1)*f32(0.1)) or 0)
    local didJump=p.wasGround and math.abs(event.y-jumpY)<=0.000002
    if didJump and sprint then
        local jx,jz=correctedJumpImpulse(p,physicalYaw,matched.fast)
        ax,az=ax+jx,az+jz
    end
    if expectedDamping==nil and dampingCount>1 then
        for damping in pairs(fittingDamping) do
            local nx,nz=inputVector(physicalYaw,f,s,damping,
                acceleration(p.origin,matched.ground,speedSprint),matched.fast)
            if didJump and sprint then
                local jx,jz=correctedJumpImpulse(p,physicalYaw,matched.fast)
                nx,nz=nx+jx,nz+jz
            end
            if math.abs(nx-ax)>0.000001 or math.abs(nz-az)>0.000001 then
                p.failed=true
                return event
            end
        end
    end
    if not state.mathKnown then

        for _,candidate in ipairs({false,true,"aligned"}) do
            local ox,oz=inputVector(movementYaw,f32(forward*f32(matched.slow)),f32(strafe*f32(matched.slow)),
                matched.damping,acceleration(p.origin,matched.ground,matched.sprinting),candidate)
            if matched.jump then
                local jx,jz=nativeJumpImpulse(p,matched.jumpYaw or cameraYaw,candidate)
                ox,oz=ox+jx,oz+jz
            end
            if math.abs(event.x-p.beforeX-ox)<=0.000002 and math.abs(event.z-p.beforeZ-oz)<=0.000002 then
                local nx,nz=inputVector(physicalYaw,f,s,matched.damping,
                    acceleration(p.origin,matched.ground,speedSprint),candidate)
                if didJump and sprint then
                    local jx,jz=correctedJumpImpulse(p,physicalYaw,candidate)
                    nx,nz=nx+jx,nz+jz
                end
                if math.abs(nx-ax)>0.000001 or math.abs(nz-az)>0.000001 then
                    p.failed=true
                    return event
                end
            end
        end
    end
    event.x,event.z=event.x-matched.oldX+ax,event.z-matched.oldZ+az

    local previousSprint=read(player.is_sprinting,false)
    local ok=pcall(player.set_sprinting,sprint)
    if not ok or read(player.is_sprinting,nil)~=sprint then

        event.x,event.z=event.x-ax+matched.oldX,event.z-az+matched.oldZ
        pcall(player.set_sprinting,previousSprint)
        p.failed=true; return event
    end

    local previousX,previousZ=read(player.get_motion_x,nil),read(player.get_motion_z,nil)
    local wroteX,wroteZ=false,false
    if finite(previousX) and finite(previousZ) then
        wroteX=pcall(player.set_motion_x,event.x)
        wroteZ=pcall(player.set_motion_z,event.z)
    end
    local storedX,storedZ=read(player.get_motion_x,nil),read(player.get_motion_z,nil)
    if not wroteX or not wroteZ or not finite(storedX) or not finite(storedZ)
        or math.abs(storedX-event.x)>1e-9 or math.abs(storedZ-event.z)>1e-9 then
        if finite(previousX) then pcall(player.set_motion_x,previousX) end
        if finite(previousZ) then pcall(player.set_motion_z,previousZ) end
        event.x,event.z=event.x-ax+matched.oldX,event.z-az+matched.oldZ
        pcall(player.set_sprinting,previousSprint)
        p.failed=true; return event
    end
    p.moved=true
    p.sprint=sprint
    p.sprintReason=sprintReason
    p.virtualForward,p.virtualStrafe=f,s
    p.groundAcceleration,p.speedSprint,p.didJump=matched.ground,speedSprint,didJump
    p.damping,p.itemSlow,p.fastMath=matched.damping,correctedSlow,matched.fast
    p.correctedX,p.correctedZ=event.x,event.z
    if p.clutch then
        freezeMove(p,event)
    end
    p.placementChecked=true
    p.placedBeforeMove=placeBeforeMove(c,p)==true

    return event
end
local function placeOne(c,p)
    local function delayed(status,reason)
        p.placementStatus,p.placementReason=status,reason

        return false
    end
    if not p.allow or not setting("placement",true) then return false end
    if p.skipPlacement then return delayed("Hold",p.holdReason or p.reason) end
    if p.sent or p.updated or state.lastAttemptTick==c.tick then return false end
    if state.attackTick==c.tick then return delayed("Wait","An attack was already sent this tick") end
    local available,why=ready()
    if not available then return delayed("Paused",why) end
    if not state.poseValid or not finite(state.serverYaw) or not finite(state.serverPitch)
        or c.tick-state.lastMovementTick>2 or c.tick<state.lastMovementTick then
        return delayed("Wait","Waiting for a preceding normal movement pose")
    end
    local look,position=state.lastLookRef,state.lastPositionRef
    if look==nil or position==nil or look.cancel==true or position.cancel==true
        or not finite(look.yaw) or not finite(look.pitch)
        or math.abs(wrap(look.yaw-state.serverYaw))>0.001
        or math.abs(look.pitch-state.serverPitch)>0.001
        or not closePosition(position,state.serverPos) or not positionInOrdinaryFrame(state.serverPos,c) then
        return delayed("Wait","Previous movement pose changed / cancelled / position mismatch")
    end
    if state.serverPitch < -90 or state.serverPitch > 90 then
        return delayed("Wait","Previous applied rotation is outside pitch bounds")
    end
    if state.sentSneaking~=nil and read(player.is_sneaking,false)~=state.sentSneaking then
        return delayed("Wait","Sneak eye height changed; wait for the next ordinary pose")
    end
    if not p.telly and state.publishedSlot~=p.slot then
        return delayed("Wait","Publish the selected block slot before the first placement")
    end
    state.blocks,state.faceCache,state.rays={},{},0
    local target=p.target
    if not validTarget(c,target) then return delayed("Wait","No valid support / destination") end
    local hit=exact(p.yaw,p.pitch,target,c)
    if not hit then return delayed("Wait",state.rayReason or "Applied rotation does not hit the target face") end

    local serverOrigin={x=state.serverPos.x,z=state.serverPos.z,
        eye=state.serverPos.y+eyeHeight(state.sentSneaking==true)}
    local serverHit=projectFace(serverOrigin,p.yaw,p.pitch,target,hit.profile)
    if not serverHit or not clearFullCubePath(serverOrigin,serverHit,target) then
        return delayed("Wait","Ray differs at the preceding server position / face edge")
    end

    local precedingRequired=not p.telly or setting("raycast",false)
    if precedingRequired and not publishedSupportRay(serverOrigin,state.serverYaw,state.serverPitch,target,hit.profile) then
        return delayed("Wait","Publish a look at this support block before placing")
    end
    if setting("raycast",false) and not exact(state.serverYaw,state.serverPitch,target,c) then
        return delayed("Wait","Strict RayCast: preceding look must hit the same face")
    end
    if not p.telly and setting("raycast",false) and (math.abs(wrap(p.yaw-state.serverYaw))>0.001
        or math.abs(p.pitch-state.serverPitch)>0.001) then
        return delayed("Wait","Strict RayCast: keep the published look for this placing frame")
    end

    local slot=p.slot
    local stack=finite(slot) and utils.inventory.stack(slot) or nil
    if read(player.get_held_item_slot,nil)~=slot or not stack or not stack.allowed then
        return delayed("Wait","Prepared block slot changed; reselect on the next client tick")
    end

    local anchor=read(world.get_block_at,nil,target.x,target.y,target.z)
    if not support(anchor) or not supportAt(target.x,target.y,target.z) then
        return delayed("Wait","Support changed before the controller click")
    end
    if state.lastAttemptTick~=c.tick then state.attemptCount=0 end
    state.lastAttemptTick,state.attemptCount=c.tick,state.attemptCount+1
    local attempt={target=target,hit=hit.hit,slot=slot,packet=nil,error=nil,
        serial=state.movementSerial,yaw=p.yaw,pitch=p.pitch}
    state.sending=attempt

    local ok,placed=pcall(player.place_block,slot,
        types.vec3i.new(target.x,target.y,target.z),
        types.vec3d.new(hit.hit.x,hit.hit.y,hit.hit.z),enums.facing[target.face.name])
    state.sending=nil
    if attempt.packet then
        local error,known=validatePlacementPacket(attempt,attempt.packet)
        attempt.error=attempt.error or error;attempt.metadataKnown=known
    end

    if attempt.packet and attempt.packet.cancel~=true then
        p.placementYaw,p.placementPitch,p.clickedTarget=attempt.yaw,attempt.pitch,target
        p.placementPacket=attempt.packet
    end
    local x,y,z=target.x+target.face.x,target.y+target.face.y,target.z+target.face.z
    local key=cellKey(x,y,z)
    if p.placementPacket==attempt.packet and attempt.packet~=nil then

        local localBlock=read(world.get_block_at,nil,x,y,z)
        state.pending[key]={x=x,y=y,z=z,tick=c.tick,packet=attempt.packet,
            yaw=attempt.yaw,pitch=attempt.pitch,serial=attempt.serial,
            metadataKnown=attempt.metadataKnown,
            supportKey=cellKey(target.x,target.y,target.z),
            seen=localBlock~=nil and not replaceable(localBlock)}
    end
    if attempt.error or (attempt.packet and attempt.packet.cancel==true) then
        state.rejected[key]=c.tick+40
        state.cooldownUntil=math.max(state.cooldownUntil,c.tick+5)
        return delayed("Rejected",attempt.error or "Own C08 cancelled by another hook")
    elseif ok and placed==true and attempt.packet~=nil then
        state.lastTarget,state.targetTick=target,c.tick
        state.lastPlaceDelta=state.lastYawDelta or 0
        state.lastPlaceSerial=state.movementSerial
        p.placementYaw,p.placementPitch,p.clickedTarget=attempt.yaw,attempt.pitch,target
        p.rotationLocked=true
        state.placementPrimed=true
        if setting("no-swing",false) then pcall(player.send_packet,0x0A,{})
        else pcall(player.swing_item) end
        return true
    else
        return delayed("Wait",ok and "Placement refused or C08 callback unavailable" or "Placement API failed")
    end
    return false
end

placeBeforeMove=function(c,p) return placeOne(c,p) end

local function update(event)
    if event==nil or type(event)=="boolean" then return event end
    local pre=phase(event)
    state.stage=pre==true and "updatePRE" or (pre==false and "updatePOST" or "update?")
    if pre==false then restoreFreeze();return event end
    if pre~=true then return event end

    restoreFreeze()
    local c=context()
    local p=state.plan
    if not c or not p or p.tick~=c.tick or not p.moved or p.failed then return event end

    if read(player.is_sprinting,nil)~=p.sprint then
        p.incoherent=true;state.cooldownUntil=math.max(state.cooldownUntil,c.tick+2)
    end

    event.yaw,event.pitch=p.yaw,p.pitch
    p.updated=true;p.position={x=c.x,y=c.y,z=c.z}
    state.yaw,state.pitch=p.yaw,p.pitch
    return event
end
local function packet(event)
    if event==nil or type(event)=="boolean" then return event end
    local id=event.packet_id
    if not finite(id) then return event end
    if id==0x09 then
        state.lastSlotRef=event
        state.publishedSlot=nil;state.placementPrimed=false
        local held=read(player.get_held_item_slot,nil)
        local reported=read(function() return event.slot end,nil)
        if not finite(reported) then reported=held end
        state.syncedSlot=event.cancel~=true and reported or nil
        return event
    end
    if id==0x02 then
        if event.cancel~=true then state.attackTick=read(player.get_ticks_existed,-1) end
        return event
    end
    if id==0x08 then
        local a=state.sending
        if a then
            a.packet=event
            if a.serial~=state.movementSerial then a.error="A new movement packet preceded this placement" end

            if a.serial==state.movementSerial then alignPlacementPacket(a,event) end
            local error,known=validatePlacementPacket(a,event)
            a.error=a.error or error;a.metadataKnown=known
        end
        return event
    end
    if event.cancel==true then return event end
    if id<0x03 or id>0x06 then return event end
    local c=context()
    if c==nil then return event end
    local p=state.plan
    if p and p.tick==c.tick and p.moved and not p.failed
        and read(player.is_sprinting,nil)~=p.sprint then
        p.incoherent=true
        state.cooldownUntil=math.max(state.cooldownUntil,c.tick+2)
    end
    if (id==0x05 or id==0x06) and p and p.tick==c.tick and p.moved and not p.failed then
        event.yaw,event.pitch=p.yaw,p.pitch
    end
    if p and p.tick==c.tick and p.moved and not p.failed then p.updated=true end
    if (id==0x05 or id==0x06) and finite(event.yaw) and finite(event.pitch) then
        state.lastYawDelta=math.abs(wrap(event.yaw-(state.serverYaw or c.yaw)))
        state.serverYaw,state.serverPitch=event.yaw,event.pitch
        state.lastLookRef=event
    end
    if (id==0x04 or id==0x06) and finite(event.x) and finite(event.y) and finite(event.z) then
        state.serverPos={x=event.x,y=event.y,z=event.z}
        state.lastPositionRef=event
    end
    state.movementSerial=state.movementSerial+1
    state.lastMovementRef=event
    local held=read(player.get_held_item_slot,nil)
    if state.syncedSlot==held and (not state.lastSlotRef or state.lastSlotRef.cancel~=true) then
        state.publishedSlot=held
    else state.publishedSlot=nil end
    state.lastMovementTick=c.tick
    state.poseValid=p~=nil and p.tick==c.tick and p.moved and not p.failed and not p.incoherent
    state.sentSneaking=read(player.is_sneaking,false)
    if state.poseValid then
        p.sent=true
        local t=p.clickedTarget
        local key=t and cellKey(t.x+t.face.x,t.y+t.face.y,t.z+t.face.z)
        local pending=key and state.pending[key]
        if pending and pending.packet==p.placementPacket and state.movementSerial>pending.serial then
            pending.followingRef=event
            pending.followingLook=state.lastLookRef
        end
    end
    state.stage="flying"
    return event
end

reset()
modules.register(ID,"Scaffold","Scaffold for Moon 1.8.9 / Telly / Silent Move Fix / Clutch",{
    on_enable=function()
        stop();claimNative()
        state.rotYaw=read(player.get_yaw,0);state.rotPitch=read(player.get_pitch,0)
    end,
    on_disable=stop,
    on_tick=function(event)
        state.stage=phase(event)==false and "tickPOST" or "tickPRE"
        if phase(event)==false then restoreFreeze() else prepare(true) end
        return event
    end,
    on_move=move,
    on_update=update,
    on_packet_send=packet,
    on_packet_receive=function(event)
        if not event or type(event)=="boolean" then return event end
        local id=event.packet_id

        if event.cancel~=true and ((id==0x12 and (event.entity_id==state.owner or event.entity_id==nil)) or id==0x27) then
            restoreFreeze()
            state.freeze=nil
            local p=state.plan
            if p then
                p.impulse=true;p.frozen=false

                if not p.moved then state.plan=nil;state.preTick=-1;state.poseValid=false end
            end
        elseif id==0x01 or id==0x07 or id==0x08 or id==0x40 then
            state.freeze=nil
            local tick=read(player.get_ticks_existed,0)

            if id~=0x08 then slotLease=nil end
            suspend();reset()
            if id==0x08 then
                state.cooldownUntil=tick+10
            end
        end
        return event
    end
})
modules.create_boolean_setting(ID,"placement","Allow Placement",true)
modules.create_boolean_setting(ID,"watchdog","Watchdog Rotation",true)
modules.create_boolean_setting(ID,"grim","Grim Rotation",false)
modules.create_boolean_setting(ID,"telly","Telly",true)
modules.create_boolean_setting(ID,"telly-shift","Telly On Shift",false)
modules.create_integer_setting(ID,"place-tick","Place Tick",4,1,6,1)
modules.create_boolean_setting(ID,"early-edge-place","Early Edge Placement",true)
modules.create_boolean_setting(ID,"auto-jump","Telly Auto Jump",true)
modules.create_boolean_setting(ID,"move-fix","Silent Move Fix",true)
modules.create_boolean_setting(ID,"sprint","Allow Sprint",true)
modules.create_boolean_setting(ID,"prefer-sprint-ray","Prefer Sprint Rays",true)
modules.create_boolean_setting(ID,"clutch","Clutch",false)
modules.create_boolean_setting(ID,"raycast","Strict Preceding RayCast",false)
modules.create_boolean_setting(ID,"most-blocks","Most Blocks Slot",false)
modules.create_boolean_setting(ID,"hypixel-block-switch","Hypixel Block Switch",false)
modules.create_boolean_setting(ID,"no-swing","No Client Swing",false)
modules.create_boolean_setting(ID,"source-jitter","Source Rotation Jitter",false)

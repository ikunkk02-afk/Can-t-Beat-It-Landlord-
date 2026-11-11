"""Original, deterministic pixel assets and Java models for Boss Landlord."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/bosslandlord'

def js(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def texture(name, base, mode):
    rng = random.Random(712 + len(name))
    im = Image.new('RGB', (32, 32), base)
    px = im.load()
    for y in range(32):
        for x in range(32):
            noise = rng.randint(-5, 5)
            grain = (x % 7 == 0 or x % 11 == 1) * -12 if mode == 'wood' else 0
            px[x,y] = tuple(max(0, min(255, v + noise + grain)) for v in base)
    if mode == 'wood':
        d = ImageDraw.Draw(im)
        for y in (7, 23): d.line((0,y,31,y), fill=tuple(max(0,v-10) for v in base))
    p = ASSETS / f'textures/block/{name}.png'; p.parent.mkdir(parents=True, exist_ok=True); im.save(p)

texture('walnut', (77, 47, 29), 'wood')
texture('felt', (26, 87, 64), 'felt')
texture('brass', (177, 140, 68), 'metal')
texture('cushion', (114, 40, 36), 'felt')

def cube(name, a, b, tex):
    return {'name': name, 'from': a, 'to': b, 'faces': {f: {'uv':[0,0,16,16], 'texture': '#'+tex} for f in ('north','south','east','west','up','down')}}

textures = {k: 'bosslandlord:block/'+k for k in ('walnut','felt','brass','cushion')}
table = [cube('Solid walnut tabletop',[-12,13,-8],[28,15,24],'walnut'),
         cube('Green felt inset',[-10.7,15,-6.7],[26.7,15.25,22.7],'felt')]
for x in (-10.9,26.6): table.append(cube('Brass edge', [x,15,-6.9],[x+.3,15.35,22.9],'brass'))
for z in (-6.9,22.6): table.append(cube('Brass edge', [-10.9,15,z],[26.9,15.35,z+.3],'brass'))
for x in (-8,22):
    for z in (-4,18):
        table += [cube('Carved table leg',[x,0,z],[x+2,13,z+2],'walnut'), cube('Brass leg foot',[x-.1,0,z-.1],[x+2.1,1,z+2.1],'brass')]
table += [cube('Lower stretcher',[-6,4,10],[24,5.5,12],'walnut')]
chair = [cube('Seat frame',[2,6,2],[14,8,14],'walnut'), cube('Wine red cushion',[2.4,8,2.4],[13.6,9,13.6],'cushion')]
for x in (2,12):
    for z in (2,12): chair.append(cube('Chair leg',[x,0,z],[x+2,6,z+2],'walnut'))
for x in (2,12): chair.append(cube('Tall back post',[x,8,1],[x+2,22,3],'walnut'))
chair += [cube('Back rest',[3,14,1],[13,21,3],'walnut'), cube('Back cushion',[4,15,2.9],[12,20,3.5],'cushion'),
          cube('Back crest',[2,21,1],[14,22,3],'brass')]
for name, elements in [('card_table',table),('card_chair',chair)]:
    model = {'ambientocclusion':True, 'textures': {'particle': textures['walnut'], **textures}, 'elements':elements}
    js(ASSETS / f'models/block/{name}.json',model)
    js(ASSETS / f'blockstates/{name}.json',{'variants':{'':{'model':f'bosslandlord:block/{name}'}}})

# Atlas: 54 distinct fronts plus a patterned card back, all original artwork.
font_path = 'C:/Windows/Fonts/segoeuib.ttf'
symbol_path = 'C:/Windows/Fonts/seguisym.ttf'
font = ImageFont.truetype(font_path, 12)
small = ImageFont.truetype(font_path, 9)
symbol = ImageFont.truetype(symbol_path, 18)
atlas = Image.new('RGBA',(288,336),(0,0,0,0))
for card in range(55):
    im = Image.new('RGBA',(32,48),(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rectangle((1,0,30,47), fill=(247,242,223), outline=(151,136,108))
    if card == 54:
        d.rectangle((3,3,28,44), fill=(126,38,38), outline=(200,167,86))
        for y in range(6,44,5):
            for x in range(6,28,5): d.polygon([(x,y-2),(x+2,y),(x,y+2),(x-2,y)],fill=(189,126,70))
        d.rectangle((9,17,22,30), fill=(177,140,68)); d.text((10,17),'B',font=font,fill=(81,27,24))
    else:
        red = card == 53 or card < 52 and card % 2 == 1
        color = (184,45,45) if red else (33,49,49)
        if card >= 52:
            label='BIG' if card==53 else 'LIT'
            d.text((4,3),label,font=small,fill=color)
            d.text((7,19),'★',font=symbol,fill=color)
            d.text((3,35),'JOKER',font=small,fill=color)
        else:
            rank=card//4+3
            label={11:'J',12:'Q',13:'K',14:'A',15:'2'}.get(rank,str(rank))
            suit=['♠','♥','♣','♦'][card%4]
            d.text((3,0),label,font=font,fill=color)
            d.text((8,15),suit,font=symbol,fill=color)
            d.text((21,34),label,font=small,fill=color)
    atlas.alpha_composite(im,((card%9)*32,(card//9)*48))
p=ASSETS/'textures/entity/cards.png';p.parent.mkdir(parents=True,exist_ok=True);atlas.save(p)

icon = Image.new('RGBA',(32,32),(0,0,0,0))
for i in range(3):
    tile=atlas.crop((i*32,0,i*32+32,48)).resize((14,22),Image.Resampling.NEAREST)
    tile=tile.rotate((1-i)*16,Image.Resampling.NEAREST,expand=True)
    icon.alpha_composite(tile,(2+i*7,5))
p=ASSETS/'textures/item/challenge_cards.png';p.parent.mkdir(parents=True,exist_ok=True);icon.save(p)
js(ASSETS/'models/item/challenge_cards.json',{'parent':'minecraft:item/generated','textures':{'layer0':'bosslandlord:item/challenge_cards'}})
js(RES/'data/bosslandlord/recipe/challenge_cards.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:paper'},{'item':'minecraft:paper'},{'item':'minecraft:paper'},{'item':'minecraft:redstone'}],'result':{'id':'bosslandlord:challenge_cards','count':1}})
js(RES/'data/bosslandlord/tags/entity_type/bosses.json',{'replace':False,'values':['minecraft:warden','minecraft:wither','minecraft:ender_dragon']})
js(RES/'data/bosslandlord/damage_type/card_wager.json',{'message_id':'bosslandlord.cards','scaling':'never','exhaustion':0.0,'effects':'hurt'})
for tag in ('bypasses_armor','bypasses_invulnerability','bypasses_resistance','bypasses_shield','bypasses_enchantments','no_knockback','always_hurts_ender_dragons'):
    js(RES/f'data/minecraft/tags/damage_type/{tag}.json',{'replace':False,'values':['bosslandlord:card_wager']})
zh={'key.bosslandlord.open':'发起 / 返回斗地主牌局','key.categories.bosslandlord':'打不过就斗地主',
    'item.bosslandlord.challenge_cards':'生命赌局邀请牌','item.bosslandlord.challenge_cards.tip':'靠近 Boss 右键邀请；也可以直接按 F7。',
    'block.bosslandlord.card_table':'斗地主牌桌','block.bosslandlord.card_chair':'牌桌椅',
    'entity.bosslandlord.card_table':'斗地主牌桌','entity.bosslandlord.card_seat':'牌桌座位','entity.bosslandlord.card_friend':'临时牌友',
    'death.attack.bosslandlord.cards':'%1$s 输掉了与 %2$s 的生命赌局',
    'death.attack.bosslandlord.cards.player':'%1$s 输掉了与 %2$s 的生命赌局'}
en={'key.bosslandlord.open':'Start / return to Landlord match','key.categories.bosslandlord':'Boss Landlord',
    'item.bosslandlord.challenge_cards':'Wager Invitation Cards','item.bosslandlord.challenge_cards.tip':'Right-click near a boss to invite them, or press F7.',
    'block.bosslandlord.card_table':'Landlord Table','block.bosslandlord.card_chair':'Card Chair',
    'entity.bosslandlord.card_table':'Landlord Table','entity.bosslandlord.card_seat':'Card Seat','entity.bosslandlord.card_friend':'Card Buddy',
    'death.attack.bosslandlord.cards':'%1$s lost a life wager to %2$s','death.attack.bosslandlord.cards.player':'%1$s lost a life wager to %2$s'}
js(ASSETS/'lang/zh_cn.json',zh);js(ASSETS/'lang/en_us.json',en)
print('Generated table, chairs, card atlas, invitation item, recipe, tags and translations.')

import sys, zlib, struct
def load(p):
    b=open(p,'rb').read(); i=8; idat=b''
    while i<len(b):
        n,t=struct.unpack('>I4s',b[i:i+8]); d=b[i+8:i+8+n]; i+=12+n
        if t==b'IHDR': w,h,bd,ct=struct.unpack('>IIBB',d[:10])
        elif t==b'IDAT': idat+=d
    ch={0:1,2:3,4:2,6:4}[ct]; raw=zlib.decompress(idat); st=w*ch; rows=[]; prev=bytes(st); k=0
    for y in range(h):
        f=raw[k]; line=bytearray(raw[k+1:k+1+st]); k+=1+st
        for x in range(st):
            a=line[x-ch] if x>=ch else 0; u=prev[x]; c=prev[x-ch] if x>=ch else 0
            if f==1: line[x]=(line[x]+a)&255
            elif f==2: line[x]=(line[x]+u)&255
            elif f==3: line[x]=(line[x]+(a+u)//2)&255
            elif f==4:
                pa=abs(u-c);pb=abs(a-c);pc=abs(a+u-2*c); pr=a if pa<=pb and pa<=pc else (u if pb<=pc else c)
                line[x]=(line[x]+pr)&255
        rows.append(bytes(line)); prev=bytes(line)
    return w,h,ch,rows
p=sys.argv[1]; w,h,ch,rows=load(p)
x0,y0,x1,y1=[int(v) for v in sys.argv[2:6]] if len(sys.argv)>2 else (0,0,w,h)
vals=set()
for y in range(y0,y1):
    s=''
    for x in range(x0,x1):
        px=rows[y][x*ch:x*ch+ch]; a=px[-1] if ch in(2,4) else 255; v=px[0]
        vals.add((v,a))
        s+=' ' if a<128 else ('#' if v<128 else '.')
    print(s)
print('distinct (lum,alpha):',sorted(vals)[:12],len(vals))
